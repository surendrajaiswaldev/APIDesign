package com.apidesign.filter;

import com.apidesign.entity.IdempotencyKey;
import com.apidesign.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Implements the {@code Idempotency-Key} HTTP header for POST and PATCH endpoints.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Only acts on POST / PATCH. Other methods pass through untouched.</li>
 *   <li>If the {@code Idempotency-Key} header is absent, pass through.</li>
 *   <li>If a row with that key exists AND the SHA-256 of the current body matches the stored
 *       {@code requestHash} → reply with the cached {@code responseStatus} +
 *       {@code responseBody}, set {@code Idempotent-Replay: true}, return early.</li>
 *   <li>If a row exists but hashes differ → 422 with an {@code idempotency-key-conflict}
 *       {@link ProblemDetail}. The client misused the key.</li>
 *   <li>Otherwise → wrap the response, let the chain run, and after the controller returns,
 *       persist a new row if the response was 2xx. {@code copyBodyToResponse()} ensures the
 *       bytes still flow to the client.</li>
 * </ol>
 *
 * <p>Order: runs after {@code CorrelationIdFilter} (HIGHEST_PRECEDENCE) and after
 * {@code RequestCachingFilter} (HIGHEST_PRECEDENCE + 10) so MDC is set and the body is
 * already cached when we hash it. Earlier than security/rate-limit so duplicated requests
 * never count against the user's bucket.
 *
 * <p>Feature-flagged via {@code app.idempotency.enabled} (default {@code true}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyFilter.class);

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_IDEMPOTENT_REPLAY = "Idempotent-Replay";

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public IdempotencyKeyFilter(
        IdempotencyKeyRepository repository,
        ObjectMapper objectMapper,
        @Value("${app.idempotency.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (!enabled || !isMutating(request)) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > 64) {
            // Header strictly bounded by the column width — fail fast rather than truncate.
            writeProblem(
                response,
                HttpStatus.BAD_REQUEST,
                "idempotency-key-too-long",
                "Idempotency-Key must be 64 characters or fewer",
                request.getRequestURI());
            return;
        }

        // Reuse RequestCachingFilter's wrapper when present; otherwise wrap locally so we
        // can read the body twice (once to hash, once for the downstream controller).
        ContentCachingRequestWrapper cachedReq =
            request instanceof ContentCachingRequestWrapper c
                ? c
                : new ContentCachingRequestWrapper(request);

        // Forcibly drain the input stream so getContentAsByteArray() returns the body bytes.
        cachedReq.getInputStream().readAllBytes();
        byte[] body = cachedReq.getContentAsByteArray();
        String requestHash = sha256Hex(body);

        Optional<IdempotencyKey> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (!row.getRequestHash().equals(requestHash)) {
                log.warn(
                    "Idempotency-Key reused with different body: key={} endpoint={}",
                    key,
                    request.getRequestURI());
                writeProblem(
                    response,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "idempotency-key-conflict",
                    "Idempotency-Key reused with a different request body",
                    request.getRequestURI());
                return;
            }
            log.info("Replaying idempotent response for key={} endpoint={}", key, row.getEndpoint());
            response.setStatus(row.getResponseStatus());
            response.setHeader(HEADER_IDEMPOTENT_REPLAY, "true");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (row.getResponseBody() != null) {
                byte[] bytes = row.getResponseBody().getBytes(StandardCharsets.UTF_8);
                response.setContentLength(bytes.length);
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
            }
            return;
        }

        // Wrap the response so we can capture the body after the controller runs.
        ContentCachingResponseWrapper cachedResp = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedReq, cachedResp);

            int status = cachedResp.getStatus();
            if (status >= 200 && status < 300) {
                String responseBody =
                    new String(cachedResp.getContentAsByteArray(), StandardCharsets.UTF_8);
                try {
                    IdempotencyKey row =
                        IdempotencyKey.builder()
                            .idempotencyKey(key)
                            .requestHash(requestHash)
                            .responseStatus(status)
                            .responseBody(responseBody)
                            .endpoint(request.getMethod() + " " + request.getRequestURI())
                            .userId(resolveUserId(request))
                            .build();
                    repository.save(row);
                } catch (Exception ex) {
                    // Race: another concurrent request may have inserted the same key.
                    // Don't fail the user-visible response just because we lost the persist race.
                    log.warn(
                        "Failed to persist IdempotencyKey row for key={}: {}",
                        key,
                        ex.getMessage());
                }
            }
        } finally {
            cachedResp.copyBodyToResponse();
        }
    }

    private static boolean isMutating(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    private static String resolveUserId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : principal.getName();
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input == null ? new byte[0] : input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void writeProblem(
        HttpServletResponse response,
        HttpStatus status,
        String typeSlug,
        String detail,
        String instance)
        throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(typeSlug);
        pd.setType(URI.create("https://example.com/probs/" + typeSlug));
        if (instance != null) {
            pd.setInstance(URI.create(instance));
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        byte[] body = objectMapper.writeValueAsBytes(pd);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}
