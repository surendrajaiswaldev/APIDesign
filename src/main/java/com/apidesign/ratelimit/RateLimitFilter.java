package com.apidesign.ratelimit;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.ratelimit.BucketSnapshot;
import com.apidesign.dto.ratelimit.RateLimitStatusResponse;
import com.apidesign.ratelimit.RateLimitProperties.EndpointRateLimit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiter keyed on the authenticated user (preferred) or IP address.
 *
 * Per-endpoint overrides match against the request path with {@link AntPathMatcher}.
 * Auth, health/info, Swagger, and the rate-limit introspection paths are skipped.
 *
 * On reject, emits 429 with a {@link ProblemDetail} body and the conventional rate-limit
 * headers (Retry-After / X-RateLimit-Limit / X-RateLimit-Remaining / X-RateLimit-Reset).
 *
 * Emits three Micrometer counters/gauges for observability — see
 * {@code rate_limit.cache.size}, {@code rate_limit.requests.total},
 * {@code rate_limit.rejections.total}.
 */
@Component
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final String[] SKIP_PATTERNS = {
        "/auth/login",
        "/auth/register",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/api-docs/**",
        "/v3/api-docs/**",
        "/rate-limit/status",
        "/admin/rate-limit/**"
    };

    private static final String METRIC_REQUESTS = "rate_limit.requests.total";
    private static final String METRIC_REJECTIONS = "rate_limit.rejections.total";

    private static final String OUTCOME_ALLOWED = "allowed";
    private static final String OUTCOME_REJECTED = "rejected";
    private static final String OUTCOME_SKIPPED = "skipped";

    private static final long MAX_CACHE_SIZE = 100_000L;

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final LoadingCache<String, Bucket> buckets;

    public RateLimitFilter(
        RateLimitProperties props, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(this::buildBucket);

        Gauge.builder("rate_limit.cache.size", buckets, c -> c.estimatedSize())
            .description("Number of active rate-limit buckets currently held by the Caffeine cache")
            .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (!props.enabled()) {
            recordRequest("ip", OUTCOME_SKIPPED, "default");
            chain.doFilter(request, response);
            return;
        }

        String path = pathWithinContext(request);
        if (shouldSkip(path)) {
            recordRequest(isAuthenticated() ? "user" : "ip", OUTCOME_SKIPPED, "default");
            chain.doFilter(request, response);
            return;
        }

        EndpointRateLimit override = matchOverride(path);
        boolean authenticated = isAuthenticated();
        String key = buildKey(request, path, authenticated, override);
        String keyType = authenticated ? "user" : "ip";
        String endpointTag = override != null ? override.pathPattern() : "default";

        Bucket bucket = buckets.get(key);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long limit = effectiveLimit(authenticated, override);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));

        if (probe.isConsumed()) {
            recordRequest(keyType, OUTCOME_ALLOWED, endpointTag);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader(
                "X-RateLimit-Reset", String.valueOf(probe.getNanosToWaitForReset() / 1_000_000_000L));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        recordRequest(keyType, OUTCOME_REJECTED, endpointTag);
        recordRejection(keyType, endpointTag);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        problem.setType(URI.create("https://example.com/probs/rate-limited"));
        problem.setTitle("Too Many Requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ErrorCodes.RATE_LIMITED);
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);

        objectMapper.writeValue(response.getOutputStream(), problem);
        log.debug("Rate limit exceeded: key={} path={} retryAfter={}s", key, path, retryAfterSeconds);
    }

    /**
     * Cache occupancy stats exposed for health indicators / metrics. {@code size} is the
     * Caffeine estimate (eventually consistent — close enough for monitoring); {@code
     * capacity} is the hard maximum the cache is built with.
     */
    public CacheStats cacheStats() {
        return new CacheStats(buckets.estimatedSize(), MAX_CACHE_SIZE);
    }

    /** Snapshot of the Caffeine bucket cache. */
    public record CacheStats(long size, long capacity) {}

    /**
     * Returns a non-consuming snapshot of the caller's current bucket. Triggers bucket
     * creation if missing — looking at the available token count does not consume.
     */
    public RateLimitStatusResponse inspect(HttpServletRequest request) {
        String path = pathWithinContext(request);
        EndpointRateLimit override = matchOverride(path);
        boolean authenticated = isAuthenticated();
        String key = buildKey(request, path, authenticated, override);

        Bucket bucket = buckets.get(key);
        long limit = effectiveLimit(authenticated, override);
        int burst = override != null ? override.burst() : props.defaultBurst();
        long capacity = limit + burst;

        // Probe with zero tokens to read the reset window without consuming. Some Bucket4j
        // versions don't expose tryConsumeAndReturnRemaining(0); fall back to a peek-style
        // estimate built from the available tokens and refill rate.
        long available = bucket.getAvailableTokens();
        long resetInSeconds = computeResetSeconds(available, capacity, limit);

        return new RateLimitStatusResponse(
            key, authenticated ? "USER" : "IP", capacity, available, resetInSeconds);
    }

    /**
     * Lists all currently-cached buckets as snapshots, sorted by available tokens ascending
     * (most-exhausted first), then paginated according to {@code pageable}.
     */
    public Page<BucketSnapshot> listBuckets(Pageable pageable) {
        Map<String, Bucket> snapshot = buckets.asMap();
        List<BucketSnapshot> all = new ArrayList<>(snapshot.size());
        for (Map.Entry<String, Bucket> e : snapshot.entrySet()) {
            String k = e.getKey();
            long available = e.getValue().getAvailableTokens();
            long capacity = capacityFromKey(k);
            all.add(new BucketSnapshot(k, available, capacity));
        }
        all.sort(Comparator.comparingLong(BucketSnapshot::available));

        long total = all.size();
        int offset = (int) Math.min(pageable.getOffset(), total);
        int end = (int) Math.min((long) offset + pageable.getPageSize(), total);
        List<BucketSnapshot> page = all.subList(offset, end);
        return new PageImpl<>(page, pageable, total);
    }

    private Bucket buildBucket(String key) {
        boolean authenticated = key.startsWith("user:");
        EndpointRateLimit override = overrideFromKey(key);

        long limit = effectiveLimit(authenticated, override);
        int burst = override != null ? override.burst() : props.defaultBurst();
        long capacity = limit + burst;

        Bandwidth bw = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(limit, Duration.ofMinutes(1))
            .build();
        return Bucket.builder().addLimit(bw).build();
    }

    /** Recomputes the (deterministic) capacity for an existing bucket from its key. */
    private long capacityFromKey(String key) {
        boolean authenticated = key.startsWith("user:");
        EndpointRateLimit override = overrideFromKey(key);
        long limit = effectiveLimit(authenticated, override);
        int burst = override != null ? override.burst() : props.defaultBurst();
        return limit + burst;
    }

    private EndpointRateLimit overrideFromKey(String key) {
        int colon = key.indexOf('|');
        if (colon < 0) {
            return null;
        }
        String pathPattern = key.substring(colon + 1);
        for (EndpointRateLimit ep : props.endpoints().values()) {
            if (pathPattern.equals(ep.pathPattern())) {
                return ep;
            }
        }
        return null;
    }

    private long effectiveLimit(boolean authenticated, EndpointRateLimit override) {
        long base = override != null ? override.requestsPerMinute() : props.defaultRequestsPerMinute();
        if (authenticated) {
            return Math.round(base * props.authenticatedMultiplier());
        }
        return base;
    }

    /**
     * Estimate the number of whole seconds until the bucket is fully replenished.
     * Bucket4j refills {@code limit} tokens every minute, so missing tokens drain over
     * 60 seconds at a constant rate.
     */
    private static long computeResetSeconds(long available, long capacity, long refillPerMinute) {
        if (available >= capacity || refillPerMinute <= 0) {
            return 0L;
        }
        long missing = capacity - available;
        // tokens / (tokens/min) * 60s/min
        double seconds = ((double) missing / (double) refillPerMinute) * 60.0d;
        return Math.max(1L, Math.round(seconds));
    }

    private void recordRequest(String keyType, String outcome, String endpoint) {
        Counter.builder(METRIC_REQUESTS)
            .tag("key_type", keyType)
            .tag("outcome", outcome)
            .tag("endpoint", endpoint)
            .register(meterRegistry)
            .increment();
    }

    private void recordRejection(String keyType, String endpoint) {
        Counter.builder(METRIC_REJECTIONS)
            .tag("key_type", keyType)
            .tag("endpoint", endpoint)
            .register(meterRegistry)
            .increment();
    }

    private boolean shouldSkip(String path) {
        for (String pattern : SKIP_PATTERNS) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private EndpointRateLimit matchOverride(String path) {
        for (EndpointRateLimit ep : props.endpoints().values()) {
            if (ep.pathPattern() != null && MATCHER.match(ep.pathPattern(), path)) {
                return ep;
            }
        }
        return null;
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
            && auth.isAuthenticated()
            && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }

    private static String buildKey(
        HttpServletRequest request, String path, boolean authenticated, EndpointRateLimit override) {
        String principal;
        if (authenticated) {
            principal = "user:" + SecurityContextHolder.getContext().getAuthentication().getName();
        } else {
            principal = "ip:" + clientIp(request);
        }
        if (override != null) {
            // Find the key label so buildBucket can reapply the override.
            principal = principal + "|" + override.pathPattern();
        }
        return principal;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > -1 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String pathWithinContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
