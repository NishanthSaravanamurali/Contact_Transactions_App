package com.contacttx.userservice.dto.response;

import com.contacttx.userservice.entity.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserResponse {

    private final Long userId;
    private final String name;
    private final String email;
    private final String mobileNo;
    private final LocalDate dateOfBirth;
    private final UserStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserResponse(
            Long userId,
            String name,
            String email,
            String mobileNo,
            LocalDate dateOfBirth,
            UserStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.mobileNo = mobileNo;
        this.dateOfBirth = dateOfBirth;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
