package com.contacttx.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ResolveUserRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNo;

    public ResolveUserRequest() {
        // Required by Jackson.
    }

    public ResolveUserRequest(String mobileNo) {
        setMobileNo(mobileNo);
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo == null ? null : mobileNo.trim();
    }
}
