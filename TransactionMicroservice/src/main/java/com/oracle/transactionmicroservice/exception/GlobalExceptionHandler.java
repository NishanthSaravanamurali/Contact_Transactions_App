package com.oracle.transactionmicroservice.exception;

import com.oracle.transactionmicroservice.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbiddenOperation(
            ForbiddenOperationException exception,
            HttpServletRequest request) {
        String traceId = validOrGeneratedTraceId(
                request.getHeader(TRACE_ID_HEADER));
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                exception.getErrorCode(),
                exception.getMessage(),
                request.getRequestURI(),
                traceId,
                Map.of());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .header(TRACE_ID_HEADER, traceId)
                .body(response);
    }

    private String validOrGeneratedTraceId(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // Replace untrusted trace IDs instead of reflecting them.
            }
        }
        return UUID.randomUUID().toString();
    }
}
