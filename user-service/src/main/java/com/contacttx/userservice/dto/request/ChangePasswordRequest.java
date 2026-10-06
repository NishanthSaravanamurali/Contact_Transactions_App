package com.contacttx.userservice.dto.request;

import com.contacttx.userservice.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Size(max = 128, message = "Current password must not exceed 128 characters")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 12, max = 128, message = "New password must contain between 12 and 128 characters")
    @StrongPassword
    private String newPassword;

    public ChangePasswordRequest() {
        // Required by Jackson.
    }

    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
