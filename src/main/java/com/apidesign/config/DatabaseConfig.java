package com.apidesign.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** JPA repositories, transactions, and Spring Data auditing wiring. */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.apidesign.repository")
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class DatabaseConfig {

    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager txn = new JpaTransactionManager();
        txn.setEntityManagerFactory(entityManagerFactory);
        return txn;
    }

    /**
     * Resolves "who made this change?" for {@code @CreatedBy}/{@code @LastModifiedBy} fields.
     * Uses the authenticated principal's name (the user's email in this app); falls back to
     * {@code "system"} for unauthenticated work (startup tasks, scheduled jobs, anonymous flows).
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of("system");
            }
            return Optional.ofNullable(auth.getName()).or(() -> Optional.of("system"));
        };
    }
}
