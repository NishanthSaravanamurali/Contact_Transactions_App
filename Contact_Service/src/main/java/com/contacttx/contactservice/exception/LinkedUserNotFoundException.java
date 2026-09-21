package com.contacttx.contactservice.exception;

public class LinkedUserNotFoundException extends RuntimeException {

    public LinkedUserNotFoundException() {
        super("No registered user was found for the supplied contact phone");
    }
}
