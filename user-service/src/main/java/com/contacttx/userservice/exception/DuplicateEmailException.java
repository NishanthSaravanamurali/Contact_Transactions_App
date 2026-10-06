package com.contacttx.userservice.exception;

public class DuplicateEmailException extends ApplicationException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
