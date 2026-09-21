package com.contacttx.contactservice.exception;

public class SelfLinkNotAllowedException extends RuntimeException {

    public SelfLinkNotAllowedException() {
        super("A user cannot link themself as a contact");
    }
}
