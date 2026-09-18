package com.contacttx.userservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateProfileRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidPartialUpdateAndTrimsIt() {
        UpdateProfileRequest request = new UpdateProfileRequest("  Alex Updated  ", null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
        assertEquals("Alex Updated", request.getName());
    }

    @Test
    void requiresAtLeastOnePermittedField() {
        UpdateProfileRequest request = new UpdateProfileRequest(null, null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getMessage().equals("At least one profile field must be provided")));
    }

    @Test
    void rejectsAnInvalidNameAndMobileNumber() {
        UpdateProfileRequest request = new UpdateProfileRequest("Alex", "12345");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertEquals(2, violations.size());
    }
}
