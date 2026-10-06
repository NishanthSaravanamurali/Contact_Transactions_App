package com.contacttx.userservice.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveUserRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAndTrimsAValidMobileNumber() {
        ResolveUserRequest request = new ResolveUserRequest(" 9876543210 ");

        assertTrue(validator.validate(request).isEmpty());
        assertEquals("9876543210", request.getMobileNo());
    }

    @Test
    void rejectsAnInvalidMobileNumber() {
        ResolveUserRequest request = new ResolveUserRequest("12345");

        assertFalse(validator.validate(request).isEmpty());
    }
}
