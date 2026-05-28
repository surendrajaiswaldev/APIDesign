package com.apidesign.repository;

import com.apidesign.entity.ApplicationException;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persists exception audit records. Read paths support diagnostics by correlation ID.
 */
@Repository
public interface ApplicationExceptionRepository extends JpaRepository<ApplicationException, Long> {

    Page<ApplicationException> findByCorrelationIdOrderByCreatedAtDesc(
        String correlationId, Pageable pageable);

    /** Count of exception rows created after {@code threshold} — used by the health indicator. */
    long countByCreatedAtAfter(LocalDateTime threshold);
}
