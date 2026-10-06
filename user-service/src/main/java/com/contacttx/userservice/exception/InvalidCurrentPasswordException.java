package com.contacttx.userservice.exception;

public class InvalidCurrentPasswordException extends ApplicationException {

    public InvalidCurrentPasswordException() {
        super(ErrorCode.INVALID_CURRENT_PASSWORD);
    }
}
