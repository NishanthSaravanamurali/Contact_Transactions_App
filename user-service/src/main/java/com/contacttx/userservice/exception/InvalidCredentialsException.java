package com.contacttx.userservice.exception;

public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
