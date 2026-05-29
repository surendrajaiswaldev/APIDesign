package com.apidesign.saga;

/**
 * Named steps inside the create-order saga. The orchestrator runs them in declared order
 * and compensates in reverse on failure. Persisted on {@link com.apidesign.entity.OrderSaga}
 * as {@code currentStep} so a crashed saga's state is greppable from the DB.
 */
public enum SagaStep {
    RESERVE_STOCK,
    CHARGE_PAYMENT,
    SCHEDULE_SHIPPING,
    COMPLETE_ORDER
}
