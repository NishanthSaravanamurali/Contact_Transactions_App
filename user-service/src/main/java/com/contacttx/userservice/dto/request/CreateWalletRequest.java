package com.contacttx.userservice.dto.request;

public class CreateWalletRequest {

    private final Long userId;

    public CreateWalletRequest(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
