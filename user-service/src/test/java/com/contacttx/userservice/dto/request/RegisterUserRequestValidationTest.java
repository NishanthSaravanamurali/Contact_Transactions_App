package com.contacttx.userservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterUserRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRegistrationAndTrimsTextFields() {
        RegisterUserRequest request = validRequest();

        Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.getName()).isEqualTo("Alex Johnson");
        assertThat(request.getEmail()).isEqualTo("Alex@Example.com");
        assertThat(request.getMobileNo()).isEqualTo("9876543210");
    }

    @Test
    void rejectsWeakPassword() {
        RegisterUserRequest request = validRequest();
        request.setPassword("onlylowercase");

        assertThat(propertyNames(validator.validate(request))).contains("password");
    }

    @Test
    void rejectsUserYoungerThanEighteen() {
        RegisterUserRequest request = validRequest();
        request.setDateOfBirth(LocalDate.now().minusYears(18).plusDays(1));

        assertThat(propertyNames(validator.validate(request))).contains("dateOfBirth");
    }

    private RegisterUserRequest validRequest() {
        return new RegisterUserRequest(
                "  Alex Johnson  ",
                "  Alex@Example.com  ",
                "Strong@Password123",
                "  9876543210  ",
                LocalDate.of(2000, 6, 15));
    }

    private Set<String> propertyNames(Set<ConstraintViolation<RegisterUserRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
