package com.apidesign.observability;

import com.apidesign.util.CorrelationIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs first in the filter chain. Reads or generates {@code X-Correlation-Id}, puts it on
 * MDC for logging, and emits it as a response header before downstream handlers run so it
 * always lands on the client — even when an exception aborts processing later.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationIdUtil.getCorrelationIdHeader());
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdUtil.generateCorrelationId();
        }
        CorrelationIdUtil.setCorrelationId(correlationId);
        response.setHeader(CorrelationIdUtil.getCorrelationIdHeader(), correlationId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info(
                "{} {} -> {} in {}ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs);
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}
