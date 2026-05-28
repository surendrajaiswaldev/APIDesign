package com.apidesign.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Spring AOP Aspect for cross-cutting concerns at service layer.
 *
 * AOP Benefits:
 * - Separates business logic from infrastructure concerns
 * - Centralizes logging, monitoring, and auditing
 * - Reduces code duplication in service methods
 * - Works at compile/load time with bytecode weaving
 *
 * Pointcut Types:
 * - execution: Matches method execution (most common for service layer)
 * - call: Matches method calls (less common in Spring)
 * - @annotation: Matches methods with specific annotation
 * - within: Matches types
 *
 * Advice Types:
 * - @Before: Before method execution
 * - @After: After method returns (no matter what)
 * - @AfterReturning: After successful return
 * - @AfterThrowing: After exception thrown
 * - @Around: Wraps entire method (most powerful, use sparingly)
 *
 * Performance Considerations:
 * - Pointcuts are matched early, only matching pointcuts are woven
 * - Avoid overly broad pointcuts (e.g., execution(* *.*(..)))
 * - Use specific package/class patterns
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Pointcut definition for all service layer methods.
     *
     * Pointcut expression breakdown:
     * execution(*                           - Any return type
     * com.apidesign.service..               - In service package/subpackages
     * *Service.                             - Class ending with "Service"
     * *(..))                                - Any method with any arguments
     *
     * Benefits of named pointcut:
     * - Reusable in multiple advice methods
     * - Easier to maintain (change pointcut in one place)
     * - Better readability
     */
    @Pointcut("execution(* com.apidesign.service..*Service.*(..))")
    private void serviceLayerExecution() {
        // Pointcut definition - no implementation needed
    }

    /**
     * @Before advice: Executes before service method.
     * Logs method name and arguments.
     *
     * ProceedingJoinPoint vs JoinPoint:
     * - JoinPoint: Used in @Before/@After/etc (provides info only)
     * - ProceedingJoinPoint: Used in @Around (can invoke actual method)
     *
     * Note: Arguments are already evaluated, be careful with large objects
     *
     * @param joinPoint the join point
     */
    @Before("serviceLayerExecution()")
    public void logServiceMethodEntry(org.aspectj.lang.JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // Log with correlation ID (automatically from MDC)
        log.debug("Entering {}.{} - Arguments: {}",
                className,
                methodName,
                Arrays.toString(joinPoint.getArgs()));
    }

    /**
     * @Around advice: Wraps entire method execution.
     * Most powerful - can execute method, modify args/returns, handle exceptions.
     *
     * Use cases:
     * - Timing method execution (performance monitoring)
     * - Transaction boundaries
     * - Caching layer
     * - Auditing with timing data
     *
     * Important: Always call proceed() to execute actual method!
     *
     * @param joinPoint the join point
     * @return the method's return value
     * @throws Throwable if method throws exception
     */
    @Around("serviceLayerExecution()")
    public Object trackMethodExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        try {
            // Execute actual method and capture return value
            Object result = joinPoint.proceed();

            // Log successful execution with timing
            long duration = System.currentTimeMillis() - startTime;
            log.info("Method {}.{} completed in {}ms",
                    className,
                    methodName,
                    duration);

            return result;

        } catch (Throwable throwable) {
            // Log method failure with timing info
            long duration = System.currentTimeMillis() - startTime;
            log.error("Method {}.{} failed after {}ms with exception: {}",
                    className,
                    methodName,
                    duration,
                    throwable.getMessage());

            // Re-throw to allow normal exception handling
            throw throwable;
        }
    }

    /**
     * @AfterThrowing advice: Executes when method throws exception.
     * Useful for exception-specific logging and metrics.
     *
     * Advanced: Can prevent exception from propagating in @Around
     *
     * @param joinPoint the join point
     * @param exception the thrown exception
     */
    @AfterThrowing(pointcut = "serviceLayerExecution()", throwing = "exception")
    public void logServiceMethodException(org.aspectj.lang.JoinPoint joinPoint, Exception exception) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // Log exception with stack trace and correlation ID
        log.error("Exception in {}.{}: {}",
                className,
                methodName,
                exception.getMessage(),
                exception);

        // Could send to monitoring system, alert team, etc.
    }
}

