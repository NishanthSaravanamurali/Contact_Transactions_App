package com.contacttx.contactservice.exception;

import com.contacttx.contactservice.config.TraceIdFilter;
import com.contacttx.contactservice.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                )
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                )
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is missing or malformed",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_VALUE",
                "A request value has an invalid format",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ContactNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleContactNotFound(
            ContactNotFoundException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "CONTACT_NOT_FOUND",
                "Contact not found",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(LinkedUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleLinkedUserNotFound(
            LinkedUserNotFoundException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "LINKED_USER_NOT_FOUND",
                "No registered user was found for the supplied contact phone",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(LinkedUserInactiveException.class)
    public ResponseEntity<ApiErrorResponse> handleLinkedUserInactive(
            LinkedUserInactiveException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "LINKED_USER_INACTIVE",
                "The linked user is not active",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(SelfLinkNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleSelfLinkNotAllowed(
            SelfLinkNotAllowedException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "SELF_LINK_NOT_ALLOWED",
                "A user cannot link themself as a contact",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUserServiceUnavailable(
            UserServiceUnavailableException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "USER_SERVICE_UNAVAILABLE",
                "User Service is currently unavailable",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_CONFLICT",
                "The requested change conflicts with existing data rules",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        String traceId = getTraceId(request);

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                errorCode,
                message,
                request.getRequestURI(),
                traceId,
                fieldErrors
        );

        logError(status, errorCode, request.getRequestURI(), traceId);

        return ResponseEntity
                .status(status)
                .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                .body(response);
    }

    private String getTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        if (traceId instanceof String traceIdValue && !traceIdValue.isBlank()) {
            return traceIdValue;
        }

        return UUID.randomUUID().toString();
    }

    private void logError(
            HttpStatus status,
            String errorCode,
            String requestPath,
            String traceId) {

        if (status.is5xxServerError()) {
            LOGGER.error(
                    "requestPath={} status={} traceId={} errorCode={}",
                    requestPath,
                    status.value(),
                    traceId,
                    errorCode
            );
            return;
        }

        LOGGER.warn(
                "requestPath={} status={} traceId={} errorCode={}",
                requestPath,
                status.value(),
                traceId,
                errorCode
        );
    }
}
