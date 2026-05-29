package com.apidesign.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@link org.springframework.scheduling.annotation.Async} processing and exposes a
 * dedicated {@code eventTaskExecutor} pool for domain event listeners.
 *
 * <p>Sized for low-latency local development; tune for production once we know the actual
 * publication rate.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bounded pool for async {@code @TransactionalEventListener} handlers. Named so it can be
     * targeted explicitly with {@code @Async("eventTaskExecutor")} — relying on the default
     * executor risks colliding with Spring's own scheduling pool.
     *
     * <p>Back-pressure: queue is capped at 100 and the rejected-execution policy is
     * {@link ThreadPoolExecutor.CallerRunsPolicy}. When the queue is full <i>and</i> the pool
     * is at {@code maxPoolSize}, additional tasks execute synchronously on the publishing
     * thread instead of being silently dropped — naturally throttling event producers.
     */
    @Bean("eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
