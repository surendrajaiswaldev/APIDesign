package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when input validation fails.
 * Corresponds to HTTP 400 Bad Request status.
 */
public class ValidationException extends BaseException {
    public ValidationException(String message) {
        super(message, ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST.value());
    }

    public ValidationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.BAD_REQUEST.value());
    }

    public ValidationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.BAD_REQUEST.value(), cause);
    }
}

