package com.contacttx.userservice.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiErrorResponse {

    private final Instant timestamp;
    private final String traceId;
    private final int status;
    private final String code;
    private final String message;
    private final Map<String, String> fieldErrors;

    public ApiErrorResponse(
            Instant timestamp,
            String traceId,
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors) {
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.status = status;
        this.code = code;
        this.message = message;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
