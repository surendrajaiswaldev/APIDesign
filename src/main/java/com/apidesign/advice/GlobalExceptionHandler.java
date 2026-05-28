package com.apidesign.advice;

import com.apidesign.exception.BaseException;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.ValidationErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler for REST API.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * - Applies to all @RestController classes
 * - All methods return JSON/XML (not views)
 * - Catches exceptions from entire application
 *
 * Benefits of centralized exception handling:
 * - Consistent error responses across API
 * - Single place to modify error format
 * - Reduces boilerplate in controllers
 * - Proper HTTP status codes
 * - Secure error messages (don't leak internals)
 *
 * Exception Handling Chain:
 * 1. Method in Controller throws exception
 * 2. Spring catches it and routes to GlobalExceptionHandler
 * 3. Appropriate @ExceptionHandler method processes it
 * 4. Response is built and sent to client
 *
 * Order of Methods (Priority):
 * Spring checks handlers in this order:
 * 1. Exact type match
 * 2. Superclass match
 * 3. Generic catch-all
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle custom application exceptions (BaseException and subclasses).
     *
     * Subclasses: BusinessLogicException, ValidationException,
     *             ResourceNotFoundException, DatabaseException
     *
     * Each subclass provides appropriate HTTP status code.
     *
     * @param ex the custom exception
     * @param request the web request
     * @return standardized error response
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(
            BaseException ex,
            WebRequest request) {

        log.warn("Custom exception occurred - Error Code: {}, Message: {}",
                ex.getErrorCode(),
                ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(
            ex.getStatusCode(),
            ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.valueOf(ex.getStatusCode()));
    }

    /**
     * Handle Bean Validation exceptions (from @Valid, @Validated).
     *
     * When validation fails on @RequestBody:
     * Spring wraps field errors in MethodArgumentNotValidException
     *
     * This handler:
     * - Extracts field validation errors
     * - Builds structured error response
     * - Returns 400 Bad Request
     *
     * @param ex the validation exception
     * @param request the web request
     * @return validation error response with field details
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // Extract field errors from binding result
        List<ValidationErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.add(ValidationErrorResponse.FieldError.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .rejectedValue(error.getRejectedValue())
                .build())
        );

        // Build validation error response
        ValidationErrorResponse errorResponse = ValidationErrorResponse.of(
            "Validation failed",
            request.getDescription(false).replace("uri=", ""),
            fieldErrors
        );

        log.warn("Validation error: {} field(s) failed validation",
                fieldErrors.size());

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle generic exceptions (IllegalArgumentException, etc.).
     *
     * Catch-all for unexpected exceptions.
     * Logs full stack trace for debugging.
     *
     * @param ex the exception
     * @param request the web request
     * @return generic error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        log.error("Illegal argument exception: {}", ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "Invalid request: " + ex.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle database constraint violations.
     *
     * Common causes:
     * - Duplicate unique key
     * - Foreign key constraint violated
     * - Data type mismatch
     *
     * @param ex the database exception
     * @param request the web request
     * @return constraint violation response
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex,
            WebRequest request) {

        log.error("Database integrity violation: {}", ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error(
            HttpStatus.CONFLICT.value(),
            "Database constraint violated. Please check your data."
        );

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Handle all other uncaught exceptions.
     *
     * This is the safety net - should rarely be reached if handlers above
     * cover major exception types.
     *
     * Security: Returns generic message to client (don't expose internals)
     *
     * @param ex the exception
     * @param request the web request
     * @return generic error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(
            Exception ex,
            WebRequest request) {

        // Log full details for debugging (not sent to client)
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        // Generic response (don't leak internal details to client)
        ApiResponse<Object> response = ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An internal server error occurred. Please contact support."
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

