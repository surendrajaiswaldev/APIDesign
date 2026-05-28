package com.apidesign.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized response structure for validation failures.
 * Contains validation errors for each field.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private String path;
    private List<FieldError> errors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    /**
     * Factory method to create ValidationErrorResponse.
     */
    public static ValidationErrorResponse of(String message, String path, List<FieldError> errors) {
        return ValidationErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(400)
                .message(message)
                .path(path)
                .errors(errors)
                .build();
    }
}

