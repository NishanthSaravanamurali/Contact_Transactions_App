package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.dto.response.LoginResponse;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.GlobalExceptionHandler;
import com.contacttx.userservice.service.UserRegistrationService;
import com.contacttx.userservice.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private UserRegistrationService registrationService;
    private AuthenticationService authenticationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registrationService = mock(UserRegistrationService.class);
        authenticationService = mock(AuthenticationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(registrationService, authenticationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheLoginTokenResponse() throws Exception {
        when(authenticationService.login(any()))
                .thenReturn(new LoginResponse("signed-jwt", "Bearer", 1800L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alex@example.com",
                                  "password": "Strong@Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));

        verify(authenticationService).login(any());
    }

    @Test
    void returnsNoContentForStatelessLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsCreatedAndTheSafeRegistrationResponse() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        when(registrationService.register(any())).thenReturn(
                new RegistrationResponse(42L, "alex@example.com", UserStatus.ACTIVE, createdAt));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-15T10:00:00"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(registrationService).register(any());
    }

    @Test
    void rejectsInvalidInputBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Correlation-ID", "registration-test-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "A",
                                  "email": "not-an-email",
                                  "password": "weak",
                                  "mobileNo": "123",
                                  "dateOfBirth": "2020-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-ID", "registration-test-trace"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Registration details are invalid"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email format is invalid"))
                .andExpect(jsonPath("$.fieldErrors.mobileNo")
                        .value("Mobile number must be a valid 10-digit number"));

        verify(registrationService, never()).register(any());
    }

    private String validRegistrationJson() {
        return """
                {
                  "name": "Alex Johnson",
                  "email": "alex@example.com",
                  "password": "Strong@Password123",
                  "mobileNo": "9876543210",
                  "dateOfBirth": "2000-06-15"
                }
                """;
    }
}
