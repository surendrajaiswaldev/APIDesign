package com.apidesign.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Activates {@code @PreAuthorize}/{@code @PostAuthorize} method security only when JWT
 * is enabled. Split out from {@code SecurityConfig} because {@code @EnableMethodSecurity}
 * cannot itself be conditionalised on a property — wrapping it in its own class lets
 * {@link ConditionalOnProperty} gate the bean post-processor that powers method security.
 *
 * Default is {@code true} (matchIfMissing); the flag has to be explicitly set to
 * {@code false} (e.g., in {@code application-local.yml}) to disable.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(
    prefix = "app.security.jwt",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MethodSecurityConfig {
}
