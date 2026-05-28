package com.apidesign.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.orm.jpa.JpaTransactionManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * JPA/Hibernate Configuration for persistence layer.
 *
 * Database Configuration Guide:
 * =============================
 *
 * Connection Pooling with HikariCP (Configured in application.yml):
 * WHY CONNECTION POOLING?
 * - Database connections are expensive to create/destroy
 * - Creating connection per request would kill performance
 * - Connection pool maintains ready connections
 * - Reuses connections across requests
 *
 * HikariCP Key Parameters:
 * 1. minimumIdle (default: 10)
 *    - Minimum connections kept alive
 *    - Low minimumIdle = lower memory, slower recovery from spikes
 *    - High minimumIdle = more memory, faster during traffic spikes
 *    - Recommendation: Set to ~70% of maximumPoolSize
 *
 * 2. maximumPoolSize (default: 20)
 *    - Maximum connections that can be created
 *    - Limits database connections per application instance
 *    - Set based on: DB max connections / (number of app instances)
 *    - Recommendation: Start with 20, adjust based on load testing
 *
 * 3. connectionTimeout (default: 30 seconds)
 *    - Time to wait for connection from pool
 *    - Too low: Errors during traffic spikes
 *    - Too high: Slow error recovery
 *    - Recommendation: 30-60 seconds
 *
 * 4. idleTimeout (default: 10 minutes)
 *    - Time before closing idle connections
 *    - Helps free database resources
 *    - Set based on DB idle connection settings
 *
 * 5. maxLifetime (default: 30 minutes)
 *    - Maximum time connection can exist
 *    - Should be less than DB's connection timeout
 *    - Prevents stale connections
 *
 * Performance Optimization Examples:
 * - Enable connection pooling (HikariCP)
 * - Set appropriate pool sizes based on load
 * - Monitor pool utilization (Micrometer metrics)
 * - Use batching for bulk operations (JPA)
 * - Enable query result caching where appropriate
 *
 * Common Connection Pool Issues:
 * 1. Pool Exhaustion
 *    - All connections in use, new requests wait
 *    - Symptoms: SlowQueryException, connection timeout errors
 *    - Causes: Long-running queries, N+1 queries, deadlocks
 *    - Solution: Optimize queries, increase pool size, use async
 *
 * 2. Connection Leaks
 *    - Connections not returned to pool
 *    - Symptoms: Gradually increasing response time
 *    - Causes: Exception handling not closing connection
 *    - Solution: Use try-with-resources, ensure cleanup in finally
 *
 * 3. Stale Connections
 *    - Database closed idle connections, application tries to use
 *    - Symptoms: "Connection refused" errors intermittently
 *    - Solution: Set testQuery, reduce idleTimeout
 *
 * JPA/Hibernate Configuration:
 * ============================
 *
 * fetch = FetchType.LAZY (default):
 * - Relationships loaded only when accessed
 * - Reduces initial query time
 * - Risk: N+1 problem (will loop back to this in service layer)
 * - Solution: Use JOIN FETCH or @EntityGraph in repository queries
 *
 * fetch = FetchType.EAGER:
 * - Relationships loaded immediately with entity
 * - Useful when you always need the relationship
 * - Downside: Wastes queries if relationship not needed
 *
 * CascadeType.PERSIST:
 * - When parent is saved, children are automatically saved
 * - Use for composition relationships (parent owns children)
 * - Don't use with REMOVE for soft deletes
 *
 * orphanRemoval = true:
 * - When parent-child relationship is removed, delete orphaned child
 * - Only works with bidirectional relationships
 * - Creates cascade delete; use carefully!
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.apidesign.repository")
@EnableJpaAuditing // Enables @CreatedDate, @LastModifiedDate annotations
public class DatabaseConfig {

    /**
     * Transaction Manager Bean.
     * Spring automatically wires JpaTransactionManager from EntityManagerFactory.
     * Explicit bean definition shown here for documentation.
     *
     * Transaction Management:
     * - @Transactional marks method as transactional
     * - Spring proxy creates transaction boundaries
     * - Rollback on unchecked exceptions (RuntimeException)
     * - Commit on successful completion
     *
     * @param entityManagerFactory JPA entity manager factory
     * @return transaction manager
     */
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager txn = new JpaTransactionManager();
        txn.setEntityManagerFactory(entityManagerFactory);
        return txn;
    }
}

