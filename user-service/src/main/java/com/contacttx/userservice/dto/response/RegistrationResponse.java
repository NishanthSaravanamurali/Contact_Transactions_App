package com.contacttx.userservice.dto.response;

import com.contacttx.userservice.entity.UserStatus;

import java.time.LocalDateTime;

public class RegistrationResponse {

    private final Long userId;
    private final String email;
    private final UserStatus status;
    private final LocalDateTime createdAt;

    public RegistrationResponse(
            Long userId,
            String email,
            UserStatus status,
            LocalDateTime createdAt) {
        this.userId = userId;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
