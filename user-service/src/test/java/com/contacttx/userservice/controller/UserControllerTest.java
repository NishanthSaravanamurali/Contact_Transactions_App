package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.request.ChangePasswordRequest;
import com.contacttx.userservice.dto.request.UpdateProfileRequest;
import com.contacttx.userservice.dto.response.UserResponse;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.security.AuthenticatedUserIdResolver;
import com.contacttx.userservice.service.UserProfileService;
import com.contacttx.userservice.service.UserPasswordService;
import com.contacttx.userservice.service.UserDeactivationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void usesOnlyTheVerifiedJwtSubjectToLoadTheProfile() {
        UserProfileService profileService = mock(UserProfileService.class);
        UserPasswordService passwordService = mock(UserPasswordService.class);
        UserDeactivationService deactivationService = mock(UserDeactivationService.class);
        UserController controller = new UserController(
                new AuthenticatedUserIdResolver(),
                profileService,
                passwordService,
                deactivationService);
        UserResponse expectedResponse = new UserResponse(
                42L,
                "Alex Johnson",
                "alex@example.com",
                "9876543210",
                LocalDate.of(2000, 6, 15),
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 15, 10, 0));
        when(profileService.getProfile(42L)).thenReturn(expectedResponse);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .build();

        ResponseEntity<UserResponse> response = controller.getMyProfile(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResponse, response.getBody());
        verify(profileService).getProfile(42L);
    }

    @Test
    void usesTheJwtSubjectForProfileUpdates() {
        UserProfileService profileService = mock(UserProfileService.class);
        UserPasswordService passwordService = mock(UserPasswordService.class);
        UserDeactivationService deactivationService = mock(UserDeactivationService.class);
        UserController controller = new UserController(
                new AuthenticatedUserIdResolver(),
                profileService,
                passwordService,
                deactivationService);
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Alex Updated", "9123456789");
        UserResponse expectedResponse = new UserResponse(
                42L,
                "Alex Updated",
                "alex@example.com",
                "9123456789",
                LocalDate.of(2000, 6, 15),
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 16, 10, 0));
        when(profileService.updateProfile(42L, request)).thenReturn(expectedResponse);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .build();

        ResponseEntity<UserResponse> response = controller.updateMyProfile(jwt, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResponse, response.getBody());
        verify(profileService).updateProfile(42L, request);
    }

    @Test
    void usesTheJwtSubjectForPasswordChanges() {
        UserProfileService profileService = mock(UserProfileService.class);
        UserPasswordService passwordService = mock(UserPasswordService.class);
        UserDeactivationService deactivationService = mock(UserDeactivationService.class);
        UserController controller = new UserController(
                new AuthenticatedUserIdResolver(),
                profileService,
                passwordService,
                deactivationService);
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Old@Password123", "New@Password456");
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .build();

        ResponseEntity<Void> response = controller.changeMyPassword(jwt, request);

        assertEquals(200, response.getStatusCode().value());
        verify(passwordService).changePassword(42L, request);
    }

    @Test
    void usesTheJwtSubjectForAccountDeactivation() {
        UserProfileService profileService = mock(UserProfileService.class);
        UserPasswordService passwordService = mock(UserPasswordService.class);
        UserDeactivationService deactivationService = mock(UserDeactivationService.class);
        UserController controller = new UserController(
                new AuthenticatedUserIdResolver(),
                profileService,
                passwordService,
                deactivationService);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .build();

        ResponseEntity<Void> response = controller.deactivateMyAccount(jwt);

        assertEquals(204, response.getStatusCode().value());
        verify(deactivationService).deactivate(42L);
    }
}
