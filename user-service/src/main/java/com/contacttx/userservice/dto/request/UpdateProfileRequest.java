package com.contacttx.userservice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(min = 5, max = 60, message = "Name must contain between 5 and 60 characters")
    private String name;

    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNo;

    public UpdateProfileRequest() {
        // Required by Jackson.
    }

    public UpdateProfileRequest(String name, String mobileNo) {
        setName(name);
        setMobileNo(mobileNo);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trim(name);
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = trim(mobileNo);
    }

    @AssertTrue(message = "At least one profile field must be provided")
    public boolean isUpdateRequested() {
        return name != null || mobileNo != null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
