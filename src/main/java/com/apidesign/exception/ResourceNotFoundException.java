package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

public final class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(message, ErrorCodes.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND.value());
    }

    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.NOT_FOUND.value());
    }

    public ResourceNotFoundException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.NOT_FOUND.value(), cause);
    }
}
