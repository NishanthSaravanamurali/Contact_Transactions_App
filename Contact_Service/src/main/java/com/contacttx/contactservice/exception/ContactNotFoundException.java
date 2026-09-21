package com.contacttx.contactservice.exception;

public class ContactNotFoundException extends RuntimeException {

    public ContactNotFoundException() {
        super("Contact not found");
    }
}
