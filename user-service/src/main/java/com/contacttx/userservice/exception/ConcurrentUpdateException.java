package com.contacttx.userservice.exception;

public class ConcurrentUpdateException extends ApplicationException {

    public ConcurrentUpdateException() {
        super(ErrorCode.CONCURRENT_UPDATE);
    }
}
