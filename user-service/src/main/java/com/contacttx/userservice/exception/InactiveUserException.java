package com.contacttx.userservice.exception;

public class InactiveUserException extends ApplicationException {

    public InactiveUserException() {
        super(ErrorCode.INACTIVE_USER);
    }
}
