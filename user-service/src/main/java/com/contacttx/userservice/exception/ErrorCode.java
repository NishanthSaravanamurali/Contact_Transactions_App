package com.contacttx.userservice.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request details are invalid"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email is already registered"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "Current password is incorrect"),
    INACTIVE_USER(HttpStatus.FORBIDDEN, "User account is not active"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "User profile was modified by another request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Operation is not permitted"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "The requested change conflicts with existing data"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
