package com.apidesign.config;

import com.apidesign.interceptor.DeprecationInterceptor;
import com.apidesign.interceptor.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors and the ETag filter.
 *
 * <ul>
 *   <li>{@link RequestInterceptor} runs for all paths except actuator, swagger, and the
 *       default error dispatch endpoint — those don't need application audit and would
 *       inflate the log + DB churn.</li>
 *   <li>{@link DeprecationInterceptor} runs <i>before</i> {@code RequestInterceptor} so the
 *       deprecation headers land on the response before audit log lines reference them.</li>
 *   <li>{@link ShallowEtagHeaderFilter} is exposed as a bean — Spring auto-registers it on
 *       every GET. The filter SHA-256s the response body, emits an {@code ETag} header, and
 *       returns 304 when the client sends a matching {@code If-None-Match}.</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestInterceptor requestInterceptor;
    private final DeprecationInterceptor deprecationInterceptor;

    public WebConfig(
        RequestInterceptor requestInterceptor, DeprecationInterceptor deprecationInterceptor) {
        this.requestInterceptor = requestInterceptor;
        this.deprecationInterceptor = deprecationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Deprecation headers must be set early so they survive even when downstream
        // interceptors short-circuit (e.g. RequestInterceptor's audit hooks).
        registry
            .addInterceptor(deprecationInterceptor)
            .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/error");
        registry
            .addInterceptor(requestInterceptor)
            .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/error");
    }

    /**
     * Exposed as a bean so Spring Boot's filter auto-registration picks it up. Once
     * registered, it hashes every successful response body, adds an {@code ETag} response
     * header, and returns 304 to subsequent requests that present a matching
     * {@code If-None-Match}. No controller changes needed.
     *
     * <p>Note: the filter does still write the full body to a buffer to compute the digest —
     * for large payloads consider switching to a true streaming digest later.
     */
    @Bean
    public ShallowEtagHeaderFilter shallowEtagHeaderFilter() {
        return new ShallowEtagHeaderFilter();
    }
}
