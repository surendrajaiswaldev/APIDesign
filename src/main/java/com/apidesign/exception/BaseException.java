package com.apidesign.exception;

/**
 * Base exception class for all application-specific exceptions.
 * Provides foundation for consistent exception handling across the application.
 */
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final int statusCode;

    /**
     * Constructs a BaseException with error message, code, and HTTP status code.
     *
     * @param message the error message
     * @param errorCode the unique error code for categorization
     * @param statusCode the HTTP status code
     */
    public BaseException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    /**
     * Constructs a BaseException with error details and root cause.
     *
     * @param message the error message
     * @param errorCode the unique error code
     * @param statusCode the HTTP status code
     * @param cause the root cause of this exception
     */
    public BaseException(String message, String errorCode, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

