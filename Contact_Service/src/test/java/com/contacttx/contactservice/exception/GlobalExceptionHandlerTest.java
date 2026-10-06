package com.contacttx.contactservice.exception;

import com.contacttx.contactservice.config.TraceIdFilter;
import com.contacttx.contactservice.dto.request.CreateContactRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void returnsValidationErrorsWithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/test/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "contactName": " A ",
                                  "contactPhone": "12345",
                                  "linkToRegisteredUser": null
                                }
                                """
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/test/contacts"))
                .andExpect(jsonPath("$.fieldErrors.contactName").exists())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.linkToRegisteredUser").exists());
    }

    @Test
    void returnsMalformedRequestWhenPhoneIsAJsonNumber() throws Exception {
        mockMvc.perform(post("/test/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "contactName": "Sam Taylor",
                                  "contactPhone": 9876543210,
                                  "linkToRegisteredUser": true
                                }
                                """
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"))
                .andExpect(content().string(not(containsString("StrictStringDeserializer"))));
    }

    @Test
    void returnsServiceUnavailableForUserServiceFailure() throws Exception {
        mockMvc.perform(get("/test/user-service-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errorCode").value("USER_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("User Service is currently unavailable"))
                .andExpect(content().string(not(containsString("connection refused"))));
    }

    @Test
    void returnsSafeNotFoundForMissingOrUnownedContact() throws Exception {
        mockMvc.perform(get("/test/contact-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("CONTACT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Contact not found"))
                .andExpect(content().string(not(containsString("contactId"))))
                .andExpect(content().string(not(containsString("ownerUserId"))))
                .andExpect(content().string(not(containsString("ContactNotFoundException"))));
    }

    @Test
    void returnsTheRequestTraceIdInHeaderAndErrorBody() throws Exception {
        String traceId = "gateway-trace-456";

        mockMvc.perform(get("/test/contact-not-found")
                        .header(TraceIdFilter.TRACE_ID_HEADER, traceId))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    void returnsNotFoundWhenMobileDoesNotResolveToAUser() throws Exception {
        mockMvc.perform(get("/test/linked-user-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LINKED_USER_NOT_FOUND"))
                .andExpect(content().string(not(containsString("9876543210"))));
    }

    @Test
    void returnsConflictWhenLinkedUserIsInactive() throws Exception {
        mockMvc.perform(get("/test/linked-user-inactive"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LINKED_USER_INACTIVE"))
                .andExpect(jsonPath("$.message").value("The linked user is not active"));
    }

    @Test
    void returnsBadRequestForSelfLinkAttempt() throws Exception {
        mockMvc.perform(get("/test/self-link"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SELF_LINK_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(
                        "A user cannot link themself as a contact"
                ));
    }

    @Test
    void hidesDatabaseDetailsFromConflictResponse() throws Exception {
        mockMvc.perform(get("/test/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY_CONFLICT"))
                .andExpect(content().string(not(containsString("ORA-02290"))))
                .andExpect(content().string(not(containsString("CK_CONTACT_NOT_SELF"))));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @RestController
    static class ErrorTestController {

        @PostMapping("/test/contacts")
        void createContact(@Valid @RequestBody CreateContactRequest request) {
        }

        @GetMapping("/test/user-service-unavailable")
        void userServiceUnavailable() {
            throw new UserServiceUnavailableException(
                    new RuntimeException("connection refused")
            );
        }

        @GetMapping("/test/contact-not-found")
        void contactNotFound() {
            throw new ContactNotFoundException();
        }

        @GetMapping("/test/linked-user-not-found")
        void linkedUserNotFound() {
            throw new LinkedUserNotFoundException();
        }

        @GetMapping("/test/linked-user-inactive")
        void linkedUserInactive() {
            throw new LinkedUserInactiveException();
        }

        @GetMapping("/test/self-link")
        void selfLink() {
            throw new SelfLinkNotAllowedException();
        }

        @GetMapping("/test/data-conflict")
        void dataConflict() {
            throw new DataIntegrityViolationException(
                    "ORA-02290: CK_CONTACT_NOT_SELF"
            );
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new RuntimeException("secret internal detail");
        }
    }
}
