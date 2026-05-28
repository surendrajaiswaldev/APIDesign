package com.apidesign.exception;

import com.apidesign.constants.ErrorCodes;
import org.springframework.http.HttpStatus;

public final class DatabaseException extends BaseException {

    public DatabaseException(String message) {
        super(message, ErrorCodes.DATABASE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public DatabaseException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public DatabaseException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.INTERNAL_SERVER_ERROR.value(), cause);
    }
}
