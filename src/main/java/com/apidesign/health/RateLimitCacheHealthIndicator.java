package com.apidesign.health;

import com.apidesign.ratelimit.RateLimitFilter;
import com.apidesign.ratelimit.RateLimitFilter.CacheStats;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN when the rate-limit bucket cache is within 5% of its hard cap.
 *
 * <p>Caffeine evicts on its own when full, so a hot cache isn't an outage — but it does
 * mean we're losing per-principal isolation as oldest buckets get dropped. Surface that
 * to ops via {@code /actuator/health} so an alert can fire before it actually matters.
 */
@Component("rateLimitCache")
public class RateLimitCacheHealthIndicator extends AbstractHealthIndicator {

    private static final double DOWN_THRESHOLD = 0.95d;

    private final RateLimitFilter rateLimitFilter;

    public RateLimitCacheHealthIndicator(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        CacheStats stats = rateLimitFilter.cacheStats();
        double usageRatio = stats.capacity() == 0 ? 0d : (double) stats.size() / stats.capacity();
        builder.withDetail("size", stats.size()).withDetail("maxSize", stats.capacity());
        if (usageRatio > DOWN_THRESHOLD) {
            builder.down().withDetail("usageRatio", usageRatio);
        } else {
            builder.up().withDetail("usageRatio", usageRatio);
        }
    }
}
