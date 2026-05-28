package com.apidesign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Main Spring Boot Application for E-Commerce Order Management System.
 *
 * Configuration Annotations:
 * @SpringBootApplication: Combines:
 *   - @Configuration: This is a Spring configuration class
 *   - @EnableAutoConfiguration: Enable auto-configuration
 *   - @ComponentScan: Scan for components in this package and subpackages
 *
 * @EnableAspectJAutoProxy: Enable Spring AOP proxy support
 *   - Required for @Aspect annotations to work
 *   - Creates proxies for beans with AOP advice
 *   - proxyTargetClass=true: Use CGLIB for class proxies (not interface proxies)
 */
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ApiDesignApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiDesignApplication.class, args);
    }

}
