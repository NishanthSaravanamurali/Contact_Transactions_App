package com.contacttx.userservice.dto.response;

import com.contacttx.userservice.entity.UserStatus;

public class InternalUserStatusResponse {

    private final Long userId;
    private final UserStatus status;
    private final String name;

    public InternalUserStatusResponse(Long userId, UserStatus status) {
        this(userId, status, null);
    }

    public InternalUserStatusResponse(Long userId, UserStatus status, String name) {
        this.name = name;
        this.userId = userId;
        this.status = status;
    }

    public String getName() { return name; }

    public Long getUserId() {
        return userId;
    }

    public UserStatus getStatus() {
        return status;
    }
}
