package com.apidesign.constants;

/**
 * Order statuses representing the lifecycle of an order in the system.
 *
 * PENDING: Order created but not yet confirmed
 * CONFIRMED: Order is confirmed and ready for processing
 * SHIPPED: Order has been shipped to customer
 * DELIVERED: Order delivered to customer
 * CANCELLED: Order cancelled by user or system
 */
public final class OrderStatus {
    private OrderStatus() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    /**
     * Checks if status transition is valid
     */
    public static boolean isValidTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }

        return switch (from) {
            case PENDING -> to.equals(CONFIRMED) || to.equals(CANCELLED);
            case CONFIRMED -> to.equals(SHIPPED) || to.equals(CANCELLED);
            case SHIPPED -> to.equals(DELIVERED);
            case DELIVERED -> false; // Terminal state
            case CANCELLED -> false; // Terminal state
            default -> false;
        };
    }
}

