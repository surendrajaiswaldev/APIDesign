package com.apidesign.advice;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.DatabaseException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.exception.ValidationException;
import com.apidesign.service.ExceptionAuditService;
import com.apidesign.util.CorrelationIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions to RFC 7807 {@link ProblemDetail} bodies. Every response carries
 * {@code errorCode} and {@code correlationId} extension members.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ExceptionAuditService exceptionAuditService;

    public GlobalExceptionHandler(ExceptionAuditService exceptionAuditService) {
        this.exceptionAuditService = exceptionAuditService;
    }

    // --- Domain exceptions -------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
        ResourceNotFoundException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.NOT_FOUND.value(), ex);
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), ex.getErrorCode(),
            request);
    }

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(
        BusinessLogicException ex, HttpServletRequest httpRequest, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode());
        audit(httpRequest, status.value(), ex);
        return problem(status, status.getReasonPhrase(), ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
        ValidationException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.BAD_REQUEST.value(), ex);
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage(),
            ex.getErrorCode(), request);
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ProblemDetail> handleDatabase(
        DatabaseException ex, HttpServletRequest httpRequest, WebRequest request) {
        log.error("Database error: {}", ex.getMessage(), ex);
        audit(httpRequest, HttpStatus.INTERNAL_SERVER_ERROR.value(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Database error",
            "An internal database error occurred", ex.getErrorCode(), request);
    }

    // --- Bean validation ---------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        List<Map<String, Object>> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", fe.getField());
            entry.put("message", fe.getDefaultMessage());
            entry.put("rejectedValue", fe.getRejectedValue());
            fieldErrors.add(entry);
        });

        audit(httpRequestOf(request), HttpStatus.BAD_REQUEST.value(), ex);
        ProblemDetail pd = buildProblem(
            HttpStatus.BAD_REQUEST, "Validation failed",
            "One or more fields failed validation",
            ErrorCodes.VALIDATION_FAILED, request);
        pd.setProperty("fieldErrors", fieldErrors);
        return new ResponseEntity<>(pd, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
        ConstraintViolationException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.BAD_REQUEST.value(), ex);
        ProblemDetail pd = buildProblem(
            HttpStatus.BAD_REQUEST,
            "Constraint violation",
            ex.getMessage(),
            ErrorCodes.VALIDATION_FAILED,
            request);
        List<Map<String, Object>> violations = ex.getConstraintViolations().stream().map(v -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", String.valueOf(v.getPropertyPath()));
            entry.put("message", v.getMessage());
            entry.put("rejectedValue", v.getInvalidValue());
            return entry;
        }).toList();
        pd.setProperty("fieldErrors", violations);
        return new ResponseEntity<>(pd, HttpStatus.BAD_REQUEST);
    }

    // --- Request parsing / params -----------------------------------------

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
        audit(httpRequestOf(request), HttpStatus.BAD_REQUEST.value(), ex);
        ProblemDetail pd = buildProblem(
            HttpStatus.BAD_REQUEST,
            "Malformed request",
            "Request body is not readable or contains an invalid value",
            ErrorCodes.INVALID_REQUEST,
            request);
        return new ResponseEntity<>(pd, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.BAD_REQUEST.value(), ex);
        String detail = "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue() + "'";
        return problem(HttpStatus.BAD_REQUEST, "Type mismatch", detail, ErrorCodes.INVALID_REQUEST,
            request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
        MissingServletRequestParameterException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
        audit(httpRequestOf(request), HttpStatus.BAD_REQUEST.value(), ex);
        ProblemDetail pd = buildProblem(
            HttpStatus.BAD_REQUEST,
            "Missing parameter",
            "Required parameter '" + ex.getParameterName() + "' is missing",
            ErrorCodes.INVALID_REQUEST,
            request);
        return new ResponseEntity<>(pd, HttpStatus.BAD_REQUEST);
    }

    // --- Persistence / concurrency ----------------------------------------

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
        OptimisticLockingFailureException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.CONFLICT.value(), ex);
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
            "The resource was modified by another request. Retry with the latest version.",
            ErrorCodes.OPTIMISTIC_LOCK, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(
        DataIntegrityViolationException ex, HttpServletRequest httpRequest, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        audit(httpRequest, HttpStatus.CONFLICT.value(), ex);
        return problem(HttpStatus.CONFLICT, "Data integrity violation",
            "Database constraint violated. Please check your data.",
            ErrorCodes.CONSTRAINT_VIOLATION, request);
    }

    // --- Security ----------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
        AccessDeniedException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.FORBIDDEN.value(), ex);
        return problem(HttpStatus.FORBIDDEN, "Access denied",
            "You do not have permission to access this resource",
            ErrorCodes.AUTH_ACCESS_DENIED, request);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ProblemDetail> handleAuthentication(
        AuthenticationException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.UNAUTHORIZED.value(), ex);
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
            ex.getMessage() == null ? "Authentication required" : ex.getMessage(),
            ErrorCodes.AUTH_INVALID_CREDENTIALS, request);
    }

    // --- Fallback ----------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
        IllegalArgumentException ex, HttpServletRequest httpRequest, WebRequest request) {
        audit(httpRequest, HttpStatus.BAD_REQUEST.value(), ex);
        return problem(HttpStatus.BAD_REQUEST, "Invalid argument",
            ex.getMessage() == null ? "Invalid request" : ex.getMessage(),
            ErrorCodes.INVALID_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(
        Exception ex, HttpServletRequest httpRequest, WebRequest request) {
        log.error("Unhandled exception", ex);
        audit(httpRequest, HttpStatus.INTERNAL_SERVER_ERROR.value(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
            "An internal server error occurred. Please contact support.",
            ErrorCodes.INTERNAL_ERROR, request);
    }

    // --- Helpers -----------------------------------------------------------

    private void audit(HttpServletRequest req, int status, Throwable ex) {
        try {
            exceptionAuditService.record(req, status, ex, 0L);
        } catch (Throwable t) {
            // Defensive — audit service already swallows internally, but never let the handler fail.
            log.error("Exception audit invocation failed: {}", t.getMessage(), t);
        }
    }

    private static HttpServletRequest httpRequestOf(WebRequest request) {
        if (request instanceof org.springframework.web.context.request.ServletWebRequest swr) {
            return swr.getRequest();
        }
        return null;
    }

    private static ResponseEntity<ProblemDetail> problem(
        HttpStatus status, String title, String detail, String errorCode, WebRequest request) {
        ProblemDetail pd = buildProblem(status, title, detail, errorCode, request);
        return new ResponseEntity<>(pd, status);
    }

    private static ProblemDetail buildProblem(
        HttpStatus status, String title, String detail, String errorCode, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://example.com/probs/" + slug(title)));
        String instance = request.getDescription(false);
        if (instance != null) {
            pd.setInstance(URI.create(instance.replace("uri=", "")));
        }
        pd.setProperty("errorCode", errorCode);
        String correlationId = CorrelationIdUtil.getCorrelationId();
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
        return pd;
    }

    private static String slug(String s) {
        return s == null ? "error" : s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
