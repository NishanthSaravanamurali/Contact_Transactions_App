package com.contacttx.contactservice.exception;

public class LinkedUserInactiveException extends RuntimeException {

    public LinkedUserInactiveException() {
        super("The linked user is not active");
    }
}
