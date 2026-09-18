package com.contacttx.userservice.dto.request;

import com.contacttx.userservice.validation.Adult;
import com.contacttx.userservice.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RegisterUserRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 5, max = 60, message = "Name must contain between 5 and 60 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must contain between 12 and 128 characters")
    @StrongPassword
    private String password;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNo;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @Adult
    private LocalDate dateOfBirth;

    public RegisterUserRequest() {
        // Required by Jackson.
    }

    public RegisterUserRequest(
            String name,
            String email,
            String password,
            String mobileNo,
            LocalDate dateOfBirth) {
        setName(name);
        setEmail(email);
        this.password = password;
        setMobileNo(mobileNo);
        this.dateOfBirth = dateOfBirth;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trim(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = trim(email);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = trim(mobileNo);
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
