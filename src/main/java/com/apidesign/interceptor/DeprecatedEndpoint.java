package com.apidesign.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or handler method as deprecated. The
 * {@link DeprecationInterceptor} reads this in {@code preHandle} and emits IETF
 * {@code Deprecation}, {@code Sunset}, and {@code Link} headers on the response.
 *
 * <p>Distinct from {@link java.lang.Deprecated} because that annotation is a compiler hint —
 * it does not carry sunset / replacement metadata for runtime advertising.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @DeprecatedEndpoint(
 *     sunset = "2026-12-31",
 *     replacedBy = "/users/{id}",
 *     link = "https://docs.example.com/deprecations/email-lookup")
 * @GetMapping("/email/{email}")
 * public ResponseEntity<UserDTO> getUserByEmail(...) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DeprecatedEndpoint {

    /** ISO-8601 date when the endpoint will be removed (e.g. {@code 2026-12-31}). Optional. */
    String sunset() default "";

    /** Documentation URL describing the deprecation and migration path. Optional. */
    String link() default "";

    /** Replacement endpoint path, e.g. {@code /users/{id}}. Optional. */
    String replacedBy() default "";
}
