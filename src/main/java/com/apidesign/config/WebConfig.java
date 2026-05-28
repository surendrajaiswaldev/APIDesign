package com.apidesign.config;

import com.apidesign.interceptor.RequestInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors. The {@link RequestInterceptor} runs for all paths except
 * actuator, swagger, and the default error dispatch endpoint — those don't need application
 * audit and would inflate the log + DB churn.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestInterceptor requestInterceptor;

    public WebConfig(RequestInterceptor requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(requestInterceptor)
            .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/error");
    }
}
