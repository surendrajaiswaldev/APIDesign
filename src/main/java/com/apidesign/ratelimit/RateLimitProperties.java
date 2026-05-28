package com.apidesign.ratelimit;

import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.rate-limit.*}. Endpoint-specific overrides keyed by an arbitrary
 * label; each entry must carry its own Ant-style {@code pathPattern}.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    int defaultRequestsPerMinute,
    int defaultBurst,
    double authenticatedMultiplier,
    Map<String, EndpointRateLimit> endpoints) {

    public RateLimitProperties {
        if (endpoints == null) {
            endpoints = Collections.emptyMap();
        }
    }

    public record EndpointRateLimit(String pathPattern, int requestsPerMinute, int burst) {}
}
