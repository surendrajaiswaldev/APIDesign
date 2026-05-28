package com.apidesign.constants;

/**
 * API endpoint constants for routing and documentation.
 * Maintains consistency across controllers and API documentation.
 */
public final class ApiEndpoints {
    private ApiEndpoints() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // User endpoints
    public static final String USER_BASE_PATH = "/users";
    public static final String USER_BY_ID = "/{id}";
    public static final String USER_BY_EMAIL = "/email/{email}";

    // Product endpoints
    public static final String PRODUCT_BASE_PATH = "/products";
    public static final String PRODUCT_BY_ID = "/{id}";
    public static final String PRODUCT_BY_CATEGORY = "/category/{category}";

    // Order endpoints
    public static final String ORDER_BASE_PATH = "/orders";
    public static final String ORDER_BY_ID = "/{id}";
    public static final String ORDER_BY_USER = "/user/{userId}";
    public static final String ORDER_STATUS = "/{id}/status";

    // Error mappings
    public static final String ERROR_PATH = "/error";
}

