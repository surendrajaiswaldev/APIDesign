package com.apidesign.constants;

/**
 * Standardized error codes for consistent error handling and API documentation.
 * Error codes follow pattern: ORD-XXXX where X represents different error categories
 */
public final class ErrorCodes {
    private ErrorCodes() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // User related errors
    public static final String USER_NOT_FOUND = "USER-001";
    public static final String USER_EMAIL_ALREADY_EXISTS = "USER-002";
    public static final String USER_INVALID_EMAIL = "USER-003";
    public static final String USER_INVALID_PASSWORD = "USER-004";

    // Product related errors
    public static final String PRODUCT_NOT_FOUND = "PROD-001";
    public static final String PRODUCT_OUT_OF_STOCK = "PROD-002";
    public static final String PRODUCT_INVALID_PRICE = "PROD-003";

    // Order related errors
    public static final String ORDER_NOT_FOUND = "ORD-001";
    public static final String ORDER_INVALID_STATUS_TRANSITION = "ORD-002";
    public static final String ORDER_EMPTY_ITEMS = "ORD-003";
    public static final String ORDER_ITEM_PRODUCT_NOT_FOUND = "ORD-004";
    public static final String ORDER_DUPLICATE_ITEMS = "ORD-005";
    public static final String ORDER_INSUFFICIENT_STOCK = "ORD-006";

    // Validation errors
    public static final String VALIDATION_FAILED = "VAL-001";
    public static final String INVALID_REQUEST = "VAL-002";

    // Database errors
    public static final String DATABASE_ERROR = "DB-001";
    public static final String CONSTRAINT_VIOLATION = "DB-002";

    // Generic errors
    public static final String INTERNAL_ERROR = "ERR-001";
    public static final String RESOURCE_NOT_FOUND = "ERR-002";
}

