package com.apidesign.dto.ratelimit;

/**
 * Read-only snapshot of the caller's current rate-limit bucket. Returned by
 * {@code GET /rate-limit/status} without consuming a token.
 *
 * @param key            full bucket key, e.g. {@code user:ada@example.com} or {@code ip:127.0.0.1}
 * @param keyType        {@code USER} for authenticated callers, {@code IP} for anon
 * @param limit          bucket capacity (refill rate + burst, with the authenticated
 *                       multiplier already applied)
 * @param available      tokens left in the bucket right now ({@code bucket.getAvailableTokens()})
 * @param resetInSeconds whole seconds until the next refill completes
 */
public record RateLimitStatusResponse(
    String key, String keyType, long limit, long available, long resetInSeconds) {}
