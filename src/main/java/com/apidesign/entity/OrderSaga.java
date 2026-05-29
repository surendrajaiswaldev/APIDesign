package com.apidesign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persisted control-plane row for one execution of the create-order saga. One row per
 * {@code sagaId}; the same {@code orderId} can map to multiple historical sagas if the
 * order was retried (kept in case we add retry later — schema is forward-compatible).
 *
 * <p>The orchestrator updates {@code currentState} after every step transition; a crashed
 * instance leaves the row in its last-known intermediate state for diagnostics. Full crash
 * recovery (re-driving the saga forward) is intentionally out of scope here — that's the
 * stretch goal noted in the brief.
 */
@Entity
@Table(
    name = "ORDER_SAGA",
    indexes = {
        @Index(name = "IDX_ORDER_SAGA_SAGA_ID", columnList = "SAGA_ID", unique = true),
        @Index(name = "IDX_ORDER_SAGA_ORDER_ID", columnList = "ORDER_ID"),
        @Index(name = "IDX_ORDER_SAGA_STATE", columnList = "CURRENT_STATE")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderSaga extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_saga_seq_gen")
    @SequenceGenerator(
        name = "order_saga_seq_gen",
        sequenceName = "ORDER_SAGA_SEQ",
        allocationSize = 50)
    private Long id;

    /** Externally-generated UUID (Strings, not the JDBC UUID type — Oracle portability). */
    @Column(name = "SAGA_ID", length = 64, nullable = false, unique = true)
    private String sagaId;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "CURRENT_STATE", length = 50, nullable = false)
    private String currentState;

    /** Null when the saga has completed or fully compensated — no in-flight step. */
    @Column(name = "CURRENT_STEP", length = 50)
    private String currentStep;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    /**
     * JSON-ish append-only log of {@code [{step, status, timestamp}, ...]}. Lives in a CLOB
     * so it can grow arbitrarily; an alternative would be a child table — keep it simple here.
     */
    @Lob
    @Column(name = "COMPENSATION_LOG")
    private String compensationLog;
}
