package com.apidesign.interceptor;

import com.apidesign.service.ExceptionAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs each request's start/end and triggers exception audit persistence.
 *
 * Coexists with {@code CorrelationIdFilter} (which owns the X-Correlation-Id header and
 * MDC). This interceptor adds:
 * <ul>
 *   <li>per-handler timing via {@code System.nanoTime()},</li>
 *   <li>structured request/response log lines,</li>
 *   <li>persistent recording of exceptions or 5xx responses via
 *       {@link ExceptionAuditService}, with deduplication against the global handler
 *       through the {@code exception.recorded} request attribute.</li>
 * </ul>
 */
@Component
public class RequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestInterceptor.class);
    private static final String START_NANOS_ATTR = "interceptor.startNanos";

    private final ExceptionAuditService exceptionAuditService;

    public RequestInterceptor(ExceptionAuditService exceptionAuditService) {
        this.exceptionAuditService = exceptionAuditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANOS_ATTR, System.nanoTime());
        log.info("REQ -> {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        long durationMs = computeDurationMs(request);
        int status = response.getStatus();
        log.info("REQ {} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(), status, durationMs);

        boolean alreadyRecorded =
            Boolean.TRUE.equals(request.getAttribute(ExceptionAuditService.RECORDED_ATTR));
        if (alreadyRecorded) {
            return;
        }

        if (ex != null || status >= 500) {
            exceptionAuditService.record(request, status, ex, durationMs);
        }
    }

    private static long computeDurationMs(HttpServletRequest request) {
        Object startObj = request.getAttribute(START_NANOS_ATTR);
        if (startObj instanceof Long start) {
            return (System.nanoTime() - start) / 1_000_000L;
        }
        return 0L;
    }
}
