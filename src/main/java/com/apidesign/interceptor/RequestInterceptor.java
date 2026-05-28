package com.apidesign.interceptor;

import com.apidesign.util.CorrelationIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC Interceptor for handling cross-cutting concerns at HTTP level.
 *
 * Responsibilities:
 * 1. Generate/extract correlation ID for request tracing
 * 2. Set correlation ID in MDC for logging
 * 3. Log request details (method, URL, parameters)
 * 4. Track API execution time
 * 5. Add correlation ID to response headers
 *
 * Lifecycle Methods:
 * - preHandle: Called before controller (generate correlationId)
 * - postHandle: Called after controller (log before response sent)
 * - afterCompletion: Called after response sent (cleanup MDC)
 *
 * Difference between Interceptor and AOP:
 * - Interceptor: Runs at HTTP layer (before reaching Spring core)
 * - AOP: Runs at method layer (can access method arguments, returns)
 * - Interceptor: Better for request/response concerns
 * - AOP: Better for service-layer cross-cutting concerns
 *
 * Use Interceptor for: Request ID, timing, HTTP headers
 * Use AOP for: Service logging, authentication checks, audit trails
 */
@Slf4j
@Component
public class RequestInterceptor implements HandlerInterceptor {
    private static final String REQUEST_START_TIME = "requestStartTime";

    /**
     * Pre-processing: Called BEFORE controller method execution.
     *
     * Steps:
     * 1. Extract/generate correlation ID
     * 2. Set in MDC for logging
     * 3. Store request start time for duration calculation
     * 4. Log request details
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param handler the handler (controller method)
     * @return true to continue request processing, false to stop
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract correlation ID from request header or generate new one
        String correlationId = request.getHeader(CorrelationIdUtil.getCorrelationIdHeader());
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdUtil.generateCorrelationId();
        }

        // Set correlation ID in MDC for all log statements in this request
        CorrelationIdUtil.setCorrelationId(correlationId);

        // Store request start time for calculating duration
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());

        // Log request details with correlation ID (automatically included via MDC)
        log.info("Incoming request: {} {} - Correlation ID: {}",
                request.getMethod(),
                request.getRequestURI(),
                correlationId);

        // Continue with normal request processing
        return true;
    }

    /**
     * Post-processing: Called AFTER controller method execution,
     * BEFORE response sent to client.
     *
     * Steps:
     * 1. Calculate request duration
     * 2. Log response details
     * 3. Add correlation ID to response header (optional, helpful for clients)
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param handler the handler
     * @param modelAndView the model and view (if applicable)
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, org.springframework.web.servlet.ModelAndView modelAndView) {
        // Calculate request duration
        long startTime = (long) request.getAttribute(REQUEST_START_TIME);
        long duration = System.currentTimeMillis() - startTime;

        // Log response with execution time
        log.info("Outgoing response: {} - Status: {} - Duration: {}ms",
                request.getRequestURI(),
                response.getStatus(),
                duration);

        // Add correlation ID to response header (helps clients track request)
        response.setHeader(CorrelationIdUtil.getCorrelationIdHeader(),
                          CorrelationIdUtil.getCorrelationId());
    }

    /**
     * Cleanup: Called AFTER response is sent to client.
     * IMPORTANT: Always clean up MDC to prevent leaks in thread pools.
     *
     * Spring often reuses threads; if MDC isn't cleared, the correlation ID
     * from one request might appear in logs of a completely different request!
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param handler the handler
     * @param ex any exception thrown during request processing
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Log any exceptions that occurred during request
        if (ex != null) {
            log.error("Request failed with exception", ex);
        }

        // CRITICAL: Clear correlation ID from MDC to prevent leaks in thread pool
        CorrelationIdUtil.clearCorrelationId();
    }
}

