package com.oracle.transactionmicroservice.messaging;

public class MalformedUserLifecycleEventException extends RuntimeException {

    public MalformedUserLifecycleEventException(String message) {
        super(message);
    }

    public MalformedUserLifecycleEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
