package com.apidesign.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} processor plus ShedLock so a job marked
 * {@code @SchedulerLock} only executes on one instance at a time. Required because
 * multiple deployed instances would otherwise each fire every {@code @Scheduled} job
 * on the same cron tick.
 *
 * <p>{@code defaultLockAtMostFor = PT30S} is the safety net for jobs that crash before
 * releasing the lock; individual jobs can extend the value when their work justifies it.
 * {@code usingDbTime()} bases lock timestamps on the database clock — avoids clock-skew
 * between application instances.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
