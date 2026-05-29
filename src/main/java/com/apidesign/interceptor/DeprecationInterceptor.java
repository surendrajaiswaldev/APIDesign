package com.apidesign.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads {@link DeprecatedEndpoint} off the matched handler method (or its declaring class)
 * and emits the IETF-draft deprecation headers on the response:
 *
 * <ul>
 *   <li>{@code Deprecation: true} — always (when the annotation is present)</li>
 *   <li>{@code Sunset: <RFC 1123 date>} — when {@link DeprecatedEndpoint#sunset()} is set</li>
 *   <li>{@code Link: <link>; rel="deprecation"} — when {@link DeprecatedEndpoint#link()} is set</li>
 *   <li>{@code Link: <replacedBy>; rel="successor-version"} — when
 *       {@link DeprecatedEndpoint#replacedBy()} is set</li>
 * </ul>
 *
 * Method-level annotation wins over class-level when both are present.
 */
@Component
public class DeprecationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DeprecationInterceptor.class);

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        DeprecatedEndpoint annotation = hm.getMethodAnnotation(DeprecatedEndpoint.class);
        if (annotation == null) {
            annotation = hm.getBeanType().getAnnotation(DeprecatedEndpoint.class);
        }
        if (annotation == null) {
            return true;
        }

        response.setHeader("Deprecation", "true");

        if (!annotation.sunset().isEmpty()) {
            String rfc1123 = toRfc1123(annotation.sunset());
            if (rfc1123 != null) {
                response.setHeader("Sunset", rfc1123);
            }
        }
        if (!annotation.link().isEmpty()) {
            response.addHeader("Link", "<" + annotation.link() + ">; rel=\"deprecation\"");
        }
        if (!annotation.replacedBy().isEmpty()) {
            response.addHeader(
                "Link", "<" + annotation.replacedBy() + ">; rel=\"successor-version\"");
        }

        log.debug(
            "Deprecation headers set for {} {} (sunset={}, replacedBy={})",
            request.getMethod(),
            request.getRequestURI(),
            annotation.sunset(),
            annotation.replacedBy());
        return true;
    }

    /**
     * Converts an ISO-8601 date (e.g. {@code 2026-12-31}) to its RFC 1123 representation
     * required by the {@code Sunset} header. Returns {@code null} if the input is malformed
     * — interceptor logs and skips, doesn't fail the request.
     */
    private static String toRfc1123(String isoDate) {
        try {
            LocalDate date = LocalDate.parse(isoDate);
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.of(date.atStartOfDay(), ZoneOffset.UTC));
        } catch (DateTimeParseException ex) {
            log.warn(
                "@DeprecatedEndpoint sunset='{}' is not a valid ISO date; header omitted",
                isoDate);
            return null;
        }
    }
}
