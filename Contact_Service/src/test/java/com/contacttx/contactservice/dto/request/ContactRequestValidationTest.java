package com.contacttx.contactservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactRequestValidationTest {

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
    void acceptsValidCreateRequestAndTrimsTheName() {
        CreateContactRequest request = new CreateContactRequest(
                "  Sam Taylor  ",
                "9876543210",
                true
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getContactName()).isEqualTo("Sam Taylor");
    }

    @Test
    void rejectsNameThatIsTooShortAfterTrimming() {
        CreateContactRequest request = new CreateContactRequest(
                "  A  ",
                "9876543210",
                false
        );

        assertThat(violatingFields(request)).contains("contactName");
    }

    @Test
    void rejectsNameLongerThanTheOracleColumn() {
        CreateContactRequest request = new CreateContactRequest(
                "123456789012345678901",
                "9876543210",
                false
        );

        assertThat(violatingFields(request)).contains("contactName");
    }

    @Test
    void rejectsPhoneThatIsNotExactlyTenDigits() {
        assertThat(violatingFields(new CreateContactRequest(
                "Sam Taylor", "123456789", false
        ))).contains("contactPhone");

        assertThat(violatingFields(new CreateContactRequest(
                "Sam Taylor", "12345678901", false
        ))).contains("contactPhone");

        assertThat(violatingFields(new CreateContactRequest(
                "Sam Taylor", "98765A3210", false
        ))).contains("contactPhone");
    }

    @Test
    void rejectsMissingLinkedUserChoice() {
        CreateContactRequest request = new CreateContactRequest(
                "Sam Taylor",
                "9876543210",
                null
        );

        assertThat(violatingFields(request)).contains("linkToRegisteredUser");
    }

    @Test
    void appliesTheSameValidationToUpdateRequests() {
        UpdateContactRequest request = new UpdateContactRequest(
                " A ",
                "12345",
                null
        );

        assertThat(violatingFields(request))
                .contains("contactName", "contactPhone", "linkToRegisteredUser");
    }

    @Test
    void acceptsPhoneWhenJsonUsesAString() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatCode(() -> objectMapper.readValue(
                """
                {
                  "contactName": "Sam Taylor",
                  "contactPhone": "9876543210",
                  "linkToRegisteredUser": true
                }
                """,
                CreateContactRequest.class
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsPhoneWhenJsonUsesANumber() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {
                  "contactName": "Sam Taylor",
                  "contactPhone": 9876543210,
                  "linkToRegisteredUser": true
                }
                """,
                CreateContactRequest.class
        ));
    }

    private Set<String> violatingFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
