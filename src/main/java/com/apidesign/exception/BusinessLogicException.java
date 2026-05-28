package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

public final class BusinessLogicException extends BaseException {

    public BusinessLogicException(String message) {
        super(message, ErrorCodes.INVALID_REQUEST, HttpStatus.BAD_REQUEST.value());
    }

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
