package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.request.ChangePasswordRequest;
import com.contacttx.userservice.dto.request.UpdateProfileRequest;
import com.contacttx.userservice.dto.response.UserResponse;
import jakarta.validation.Valid;
import com.contacttx.userservice.security.AuthenticatedUserIdResolver;
import com.contacttx.userservice.service.UserProfileService;
import com.contacttx.userservice.service.UserPasswordService;
import com.contacttx.userservice.service.UserDeactivationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthenticatedUserIdResolver userIdResolver;
    private final UserProfileService userProfileService;
    private final UserPasswordService userPasswordService;
    private final UserDeactivationService userDeactivationService;

    public UserController(
            AuthenticatedUserIdResolver userIdResolver,
            UserProfileService userProfileService,
            UserPasswordService userPasswordService,
            UserDeactivationService userDeactivationService) {
        this.userIdResolver = userIdResolver;
        this.userProfileService = userProfileService;
        this.userPasswordService = userPasswordService;
        this.userDeactivationService = userDeactivationService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = userIdResolver.resolve(jwt);
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = userIdResolver.resolve(jwt);
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = userIdResolver.resolve(jwt);
        userPasswordService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = userIdResolver.resolve(jwt);
        userDeactivationService.deactivate(userId);
        return ResponseEntity.noContent().build();
    }
}
