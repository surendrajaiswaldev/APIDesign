package com.apidesign.health;

import com.apidesign.repository.ApplicationExceptionRepository;
import java.time.LocalDateTime;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Surfaces the count of exceptions persisted in the last 5 minutes.
 *
 * <p>Intentionally always reports UP — flipping a load balancer probe to OUT_OF_SERVICE
 * just because users are seeing errors would be worse than the errors themselves. This
 * indicator is for dashboards and alerting; route a Prometheus alert off
 * {@code recentExceptionCount} instead of mutating health state.
 */
@Component("exceptionAudit")
public class ExceptionAuditHealthIndicator extends AbstractHealthIndicator {

    private static final long WINDOW_MINUTES = 5L;

    private final ApplicationExceptionRepository exceptionRepository;

    public ExceptionAuditHealthIndicator(ApplicationExceptionRepository exceptionRepository) {
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        long recent = exceptionRepository.countByCreatedAtAfter(since);
        builder.up()
            .withDetail("recentExceptionCount", recent)
            .withDetail("windowMinutes", WINDOW_MINUTES);
    }
}
