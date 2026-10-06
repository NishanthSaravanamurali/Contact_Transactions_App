package com.contacttx.userservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String TRACE_ID_HEADER = "X-Correlation-ID";

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception,
            HttpServletRequest request) {
        return response(exception.getErrorCode(), exception.getMessage(), Collections.emptyMap(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), safeValidationMessage(error.getDefaultMessage()));
        }

        String message = request.getRequestURI().equals("/api/v1/auth/register")
                ? "Registration details are invalid"
                : ErrorCode.VALIDATION_FAILED.getDefaultMessage();
        return response(ErrorCode.VALIDATION_FAILED, message, fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fieldErrors.putIfAbsent(
                    violation.getPropertyPath().toString(),
                    safeValidationMessage(violation.getMessage()));
        }
        return response(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                fieldErrors,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(
                ErrorCode.VALIDATION_FAILED,
                "Request body is missing or malformed",
                Collections.emptyMap(),
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return response(
                ErrorCode.CONCURRENT_UPDATE,
                ErrorCode.CONCURRENT_UPDATE.getDefaultMessage(),
                Collections.emptyMap(),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return response(
                ErrorCode.DATA_CONFLICT,
                ErrorCode.DATA_CONFLICT.getDefaultMessage(),
                Collections.emptyMap(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request) {
        return response(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getDefaultMessage(),
                Collections.emptyMap(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return response(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.getDefaultMessage(),
                Collections.emptyMap(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.error("Unexpected request failure traceId={} path={}", traceId, request.getRequestURI(), exception);
        return response(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                Collections.emptyMap(),
                request,
                traceId);
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request) {
        return response(errorCode, message, fieldErrors, request, traceId(request));
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request,
            String traceId) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                traceId,
                errorCode.getHttpStatus().value(),
                errorCode.name(),
                message,
                fieldErrors);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .header(TRACE_ID_HEADER, traceId)
                .body(body);
    }

    private String traceId(HttpServletRequest request) {
        String suppliedTraceId = request.getHeader(TRACE_ID_HEADER);
        if (suppliedTraceId != null && SAFE_TRACE_ID.matcher(suppliedTraceId).matches()) {
            return suppliedTraceId;
        }
        return UUID.randomUUID().toString();
    }

    private String safeValidationMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value" : message;
    }
}
