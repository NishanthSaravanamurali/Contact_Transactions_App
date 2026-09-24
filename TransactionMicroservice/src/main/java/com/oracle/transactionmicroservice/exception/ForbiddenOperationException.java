package com.oracle.transactionmicroservice.exception;

public class ForbiddenOperationException extends RuntimeException {

    private final String errorCode;

    public ForbiddenOperationException(String message) {
        this("FORBIDDEN_OPERATION", message);
    }

    public ForbiddenOperationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
