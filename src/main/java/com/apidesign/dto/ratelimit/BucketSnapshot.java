package com.apidesign.dto.ratelimit;

/**
 * Admin-facing snapshot of one rate-limit bucket as observed in the Caffeine cache.
 *
 * @param key       full bucket key (e.g. {@code user:ada@example.com} or {@code ip:198.51.100.7});
 *                  may carry a {@code |<pathPattern>} suffix when an endpoint override was applied
 * @param available current available tokens ({@code bucket.getAvailableTokens()})
 * @param capacity  configured bucket capacity (refill + burst, multiplier applied for user keys)
 */
public record BucketSnapshot(String key, long available, long capacity) {}
