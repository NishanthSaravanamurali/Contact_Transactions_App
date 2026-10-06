package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.LoginRequest;
import com.contacttx.userservice.dto.response.LoginResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.InvalidCredentialsException;
import com.contacttx.userservice.repository.AppUserRepository;
import com.contacttx.userservice.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void logsInAnActiveUserUsingAHashedPasswordAndReturnsAJwt() {
        AppUser user = user(UserStatus.ACTIVE);
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Strong@Password123", "stored-argon2-hash"))
                .thenReturn(true);
        when(jwtTokenService.generateAccessToken(42L, "alex@example.com"))
                .thenReturn("signed-jwt");
        when(jwtTokenService.getAccessTokenExpirySeconds()).thenReturn(1800L);

        LoginResponse response = authenticationService.login(
                new LoginRequest("  ALEX@EXAMPLE.COM  ", "Strong@Password123"));

        verify(userRepository).findByEmail("alex@example.com");
        verify(passwordEncoder).matches("Strong@Password123", "stored-argon2-hash");
        assertEquals("signed-jwt", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(1800L, response.getExpiresIn());
    }

    @Test
    void usesTheSameErrorForAnUnknownEmailAndAWrongPassword() {
        LoginRequest request = new LoginRequest("alex@example.com", "Wrong@Password123");
        when(userRepository.findByEmail("alex@example.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches("Wrong@Password123", "stored-argon2-hash"))
                .thenReturn(false);

        InvalidCredentialsException unknownEmail = assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request));
        InvalidCredentialsException wrongPassword = assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request));

        assertEquals(unknownEmail.getErrorCode(), wrongPassword.getErrorCode());
        assertEquals("Invalid email or password", unknownEmail.getMessage());
        assertEquals("Invalid email or password", wrongPassword.getMessage());
    }

    @Test
    void rejectsAnInactiveUserBeforeCheckingThePassword() {
        AppUser user = user(UserStatus.INACTIVE);
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        assertThrows(InactiveUserException.class,
                () -> authenticationService.login(
                        new LoginRequest("alex@example.com", "Strong@Password123")));

        verify(passwordEncoder, never()).matches("Strong@Password123", "stored-argon2-hash");
        verify(jwtTokenService, never()).generateAccessToken(42L, "alex@example.com");
    }

    private AppUser user(UserStatus status) {
        AppUser user = new AppUser(
                "Alex Johnson",
                "stored-argon2-hash",
                "alex@example.com",
                9876543210L,
                LocalDate.of(2000, 6, 15),
                status);
        ReflectionTestUtils.setField(user, "userId", 42L);
        return user;
    }
}
