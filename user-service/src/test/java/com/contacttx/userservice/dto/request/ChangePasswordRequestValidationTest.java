package com.contacttx.userservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePasswordRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidPasswordChange() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Old@Password123", "New@Password456");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsAMissingCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                null, "New@Password456");

        Set<ConstraintViolation<ChangePasswordRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void rejectsAWeakNewPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Old@Password123", "weakpassword");

        Set<ConstraintViolation<ChangePasswordRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty());
    }
}
