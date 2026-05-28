package com.apidesign.constants;

/**
 * Lifecycle states for an order.
 *
 * Transitions enforced by {@link #isValidTransition(OrderStatus, OrderStatus)}.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * @return true if {@code from -> to} is a permitted transition.
     */
    public static boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PENDING -> to == CONFIRMED || to == CANCELLED;
            case CONFIRMED -> to == SHIPPED || to == CANCELLED;
            case SHIPPED -> to == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
