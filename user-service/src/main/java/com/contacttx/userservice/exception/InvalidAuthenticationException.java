package com.contacttx.userservice.exception;

public class InvalidAuthenticationException extends ApplicationException {

    public InvalidAuthenticationException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
