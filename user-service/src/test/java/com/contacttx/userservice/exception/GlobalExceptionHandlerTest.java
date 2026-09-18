package com.contacttx.userservice.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void createMockMvc() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsConsistentValidationResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(GlobalExceptionHandler.TRACE_ID_HEADER, "trace-for-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(GlobalExceptionHandler.TRACE_ID_HEADER, "trace-for-test"))
                .andExpect(jsonPath("$.traceId").value("trace-for-test"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Registration details are invalid"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email format is invalid"));
    }

    @RestController
    @RequestMapping("/api/v1/auth")
    private static class ValidationController {

        @PostMapping("/register")
        void register(@Valid @RequestBody EmailRequest request) {
            // Validation happens before this method body.
        }
    }

    private static class EmailRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
