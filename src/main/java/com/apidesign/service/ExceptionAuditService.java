package com.apidesign.service;

import com.apidesign.entity.ApplicationException;
import com.apidesign.repository.ApplicationExceptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Persists exception audit records to {@code APPLICATION_EXCEPTION}.
 *
 * Recording runs in a {@code REQUIRES_NEW} transaction so it succeeds even if the calling
 * transaction is rolling back. {@code noRollbackFor = Throwable.class} keeps the audit
 * write itself from being marked for rollback by an upstream marker. All work is wrapped
 * in try/catch — persistence failure logs at ERROR but never throws.
 *
 * Gated by {@code app.audit.exceptions.enabled}; flips to a no-op when disabled.
 */
@Service
public class ExceptionAuditService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionAuditService.class);

    private static final int REQUEST_BODY_MAX = 4096;
    private static final int EXCEPTION_MESSAGE_MAX = 2048;
    private static final int STACK_TRACE_MAX = 8192;
    private static final int QUERY_STRING_MAX = 2048;
    private static final int REQUEST_PATH_MAX = 512;
    private static final int EXCEPTION_CLASS_MAX = 256;
    private static final int USER_ID_MAX = 128;
    private static final int CLIENT_IP_MAX = 64;
    private static final int HTTP_METHOD_MAX = 10;

    /** Request attribute set after a record is persisted, so the interceptor can dedupe. */
    public static final String RECORDED_ATTR = "exception.recorded";

    private final ApplicationExceptionRepository repository;
    private final boolean enabled;

    public ExceptionAuditService(
        ApplicationExceptionRepository repository,
        @Value("${app.audit.exceptions.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Throwable.class)
    public void record(HttpServletRequest req, Integer status, Throwable ex, long durationMs) {
        if (!enabled) {
            return;
        }
        try {
            if (req != null && Boolean.TRUE.equals(req.getAttribute(RECORDED_ATTR))) {
                // Already recorded by another layer (e.g., GlobalExceptionHandler).
                return;
            }

            ApplicationException entity = ApplicationException.builder()
                .correlationId(truncate(MDC.get("correlationId"), 64))
                .httpMethod(req == null ? null : truncate(req.getMethod(), HTTP_METHOD_MAX))
                .requestPath(req == null ? null : truncate(req.getRequestURI(), REQUEST_PATH_MAX))
                .queryString(req == null ? null : truncate(req.getQueryString(), QUERY_STRING_MAX))
                .requestBody(truncate(extractBody(req), REQUEST_BODY_MAX))
                .responseStatus(status)
                .exceptionClass(ex == null ? null : truncate(ex.getClass().getName(), EXCEPTION_CLASS_MAX))
                .exceptionMessage(ex == null ? null : truncate(ex.getMessage(), EXCEPTION_MESSAGE_MAX))
                .stackTrace(ex == null ? null : truncate(stackTraceOf(ex), STACK_TRACE_MAX))
                .userId(truncate(currentUserId(), USER_ID_MAX))
                .clientIp(req == null ? null : truncate(clientIp(req), CLIENT_IP_MAX))
                .durationMs(durationMs)
                .build();

            repository.save(entity);

            if (req != null) {
                req.setAttribute(RECORDED_ATTR, Boolean.TRUE);
            }
        } catch (Throwable persistFailure) {
            // Audit must never break the request — log and swallow.
            log.error(
                "Failed to persist ApplicationException record: {}",
                persistFailure.getMessage(),
                persistFailure);
        }
    }

    private static String extractBody(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        if (req instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length == 0) {
                return null;
            }
            return new String(buf, StandardCharsets.UTF_8);
        }
        // Underlying servlet may have wrapped the request (Spring sometimes nests wrappers).
        ContentCachingRequestWrapper nested = nestedCaching(req);
        if (nested != null) {
            byte[] buf = nested.getContentAsByteArray();
            if (buf.length == 0) {
                return null;
            }
            return new String(buf, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static ContentCachingRequestWrapper nestedCaching(HttpServletRequest req) {
        try {
            return org.springframework.web.util.WebUtils.getNativeRequest(
                req, ContentCachingRequestWrapper.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String stackTraceOf(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > -1 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
