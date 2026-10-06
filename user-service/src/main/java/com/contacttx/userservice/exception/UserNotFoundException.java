package com.contacttx.userservice.exception;

public class UserNotFoundException extends ApplicationException {

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
