package com.apidesign.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Wraps body-carrying requests in {@link ContentCachingRequestWrapper} so the audit pipeline
 * can re-read the body after the controller has consumed the input stream.
 *
 * Runs immediately after {@code CorrelationIdFilter} and before security/rate-limit filters
 * so cached bytes are available everywhere downstream. Skipped when the content type isn't
 * a recognised body format (avoids buffering binary uploads).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (shouldCache(request)) {
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
            chain.doFilter(wrapped, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static boolean shouldCache(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.startsWith("application/json") || ct.startsWith("application/x-www-form-urlencoded");
    }
}
