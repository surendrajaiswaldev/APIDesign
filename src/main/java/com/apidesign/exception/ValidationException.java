package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

public final class ValidationException extends BaseException {

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
