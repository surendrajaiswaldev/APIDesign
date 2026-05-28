package com.apidesign.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Envelope for SUCCESS responses only.
 *
 * Error responses are emitted as bare {@code ProblemDetail} (RFC 7807) by
 * {@code GlobalExceptionHandler}; they do NOT use this wrapper.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(LocalDateTime timestamp, int status, String message, T data) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(LocalDateTime.now(), 200, message, data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(LocalDateTime.now(), 200, message, null);
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(LocalDateTime.now(), 201, message, data);
    }
}
