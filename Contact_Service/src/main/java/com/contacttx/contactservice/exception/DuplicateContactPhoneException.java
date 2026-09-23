package com.contacttx.contactservice.exception;

public class DuplicateContactPhoneException extends RuntimeException {

    public DuplicateContactPhoneException() {
        super("A contact with this phone number already exists");
    }
}
