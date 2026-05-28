package com.apidesign.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing correlation IDs for request tracing.
 *
 * Correlation ID Pattern:
 * - Unique ID assigned to each request
 * - Propagated through all service calls
 * - Stored in SLF4J MDC (Mapped Diagnostic Context)
 * - Included in log messages via pattern: %X{correlationId}
 *
 * Benefits:
 * - Trace request flow across distributed components
 * - Match logs from multiple services for same request
 * - Debug issues by following request ID
 * - Performance monitoring per request
 */
public final class CorrelationIdUtil {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private CorrelationIdUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Gets the HTTP header name for correlation ID.
     *
     * @return header name
     */
    public static String getCorrelationIdHeader() {
        return CORRELATION_ID_HEADER;
    }

    /**
     * Gets the MDC key for correlation ID.
     *
     * @return MDC key
     */
    public static String getMdcKey() {
        return CORRELATION_ID_MDC_KEY;
    }

    /**
     * Generates a new unique correlation ID.
     * Uses UUID for guaranteed uniqueness.
     *
     * @return new correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sets correlation ID in MDC context.
     * Called by RequestInterceptor for every incoming request.
     *
     * @param correlationId the correlation ID
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
    }

    /**
     * Gets correlation ID from MDC context.
     *
     * @return correlation ID or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_MDC_KEY);
    }

    /**
     * Clears correlation ID from MDC (call after request completes).
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_MDC_KEY);
    }
}

