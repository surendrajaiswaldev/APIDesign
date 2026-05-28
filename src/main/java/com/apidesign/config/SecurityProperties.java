package com.apidesign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security-related configuration bound from {@code app.security.*}.
 *
 * {@code jwt.enabled} (default {@code true}) gates the JWT filter in
 * {@code SecurityConfig} and method-level security in {@code MethodSecurityConfig}.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, Cors cors) {

    public record Jwt(Boolean enabled, String secret, long expiryMinutes, Long refreshExpiryDays) {
        public Jwt {
            if (enabled == null) {
                enabled = Boolean.TRUE;
            }
            if (refreshExpiryDays == null) {
                refreshExpiryDays = 7L;
            }
        }
    }

    public record Cors(String allowedOrigins, String allowedMethods, String allowedHeaders) {}
}
