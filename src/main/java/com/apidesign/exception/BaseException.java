package com.apidesign.exception;

/** Root of the application's domain exception hierarchy. Sealed to the four subtypes below. */
public abstract sealed class BaseException extends RuntimeException
    permits ResourceNotFoundException, BusinessLogicException, ValidationException, DatabaseException {

    private final String errorCode;
    private final int statusCode;

    protected BaseException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    protected BaseException(String message, String errorCode, int statusCode, Throwable cause) {
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
