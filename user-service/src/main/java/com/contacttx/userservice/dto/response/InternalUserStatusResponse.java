package com.contacttx.userservice.dto.response;

import com.contacttx.userservice.entity.UserStatus;

public class InternalUserStatusResponse {

    private final Long userId;
    private final UserStatus status;

    public InternalUserStatusResponse(Long userId, UserStatus status) {
        this.userId = userId;
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public UserStatus getStatus() {
        return status;
    }
}
