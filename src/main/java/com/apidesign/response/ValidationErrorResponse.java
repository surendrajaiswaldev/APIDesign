package com.apidesign.response;

import java.util.List;

/**
 * Retained for binary/API compatibility. Field-level validation errors are now attached
 * as a {@code fieldErrors} property on the {@code ProblemDetail} emitted by
 * {@code GlobalExceptionHandler}; this record models that attached payload's shape.
 */
public record ValidationErrorResponse(List<FieldError> errors) {

    public record FieldError(String field, String message, Object rejectedValue) {}
}
