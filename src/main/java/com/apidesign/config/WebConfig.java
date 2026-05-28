package com.apidesign.config;

import com.apidesign.interceptor.RequestInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration for Spring Boot application.
 *
 * Responsibilities:
 * 1. Register HTTP interceptors
 * 2. Configure interceptor exclusions (paths to skip)
 * 3. Setup CORS, message converters if needed
 * 4. Configure view resolvers, resource handlers
 *
 * WebMvcConfigurer vs WebMvcConfigurerAdapter:
 * - WebMvcConfigurerAdapter was deprecated in Spring 5.x
 * - WebMvcConfigurer is the new approach (interfaces with default methods)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final RequestInterceptor requestInterceptor;

    public WebConfig(RequestInterceptor requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
    }

    /**
     * Register interceptors for HTTP request handling.
     *
     * Interceptor order matters:
     * - They execute in registration order for preHandle
     * - They execute in reverse order for postHandle/afterCompletion
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /**
         * Add RequestInterceptor for all HTTP requests.
         *
         * Excluded paths: Paths that DON'T need correlation ID tracking
         * - Actuator endpoints: Health checks, metrics don't need tracing
         * - Static resources: CSS, JS, images don't need request tracing
         * - Error handler: Error endpoint shouldn't require tracing
         */
        registry.addInterceptor(requestInterceptor)
                // Add paths that should NOT go through this interceptor
                .excludePathPatterns(
                    "/actuator/**",           // Spring Boot actuator endpoints
                    "/health/**",              // Health checks
                    "/error",                  // Error page
                    "/**/*.css",              // Static resources
                    "/**/*.js",
                    "/**/*.html",
                    "/**/*.ico"
                )
                .order(1);  // Order if multiple interceptors registered
    }
}

