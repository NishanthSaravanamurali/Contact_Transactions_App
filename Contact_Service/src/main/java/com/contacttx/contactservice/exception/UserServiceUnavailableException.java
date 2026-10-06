package com.contacttx.contactservice.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(Throwable cause) {
        super("User Service is currently unavailable", cause);
    }
}
