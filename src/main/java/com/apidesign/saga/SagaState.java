package com.apidesign.saga;

/**
 * Lifecycle states of an {@link com.apidesign.entity.OrderSaga}.
 *
 * <p>Forward path: {@code STARTED -> STOCK_RESERVED -> PAYMENT_CHARGED -> SHIPPING_SCHEDULED
 * -> COMPLETED}. Each transition is a small, independent transaction
 * ({@code REQUIRES_NEW}) so a downstream failure does not undo earlier commits.
 *
 * <p>Compensation path (reverse): the orchestrator walks the completed steps in reverse,
 * advancing through {@code COMPENSATING_SHIPPING}, {@code COMPENSATING_PAYMENT},
 * {@code COMPENSATING_STOCK}, then settles at {@code COMPENSATED}. {@code FAILED} is
 * reserved for the rare case where a compensation itself blows up — alerting hook.
 */
public enum SagaState {
    STARTED,
    STOCK_RESERVED,
    PAYMENT_CHARGED,
    SHIPPING_SCHEDULED,
    COMPLETED,
    COMPENSATING_SHIPPING,
    COMPENSATING_PAYMENT,
    COMPENSATING_STOCK,
    COMPENSATED,
    FAILED
}
