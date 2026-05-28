package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when business logic constraints are violated.
 * Examples: invalid order status transition, insufficient stock, duplicate items
 * Corresponds to HTTP 400 Bad Request or 409 Conflict status.
 */
public class BusinessLogicException extends BaseException {
    public BusinessLogicException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.BAD_REQUEST.value());
    }

    public BusinessLogicException(String message, String errorCode, int statusCode) {
        super(message, errorCode, statusCode);
    }

    public BusinessLogicException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.BAD_REQUEST.value(), cause);
    }
}

