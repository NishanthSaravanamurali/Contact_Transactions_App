package com.contacttx.userservice.controller;

import com.contacttx.userservice.dto.request.LoginRequest;
import com.contacttx.userservice.dto.request.RegisterUserRequest;
import com.contacttx.userservice.dto.response.LoginResponse;
import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.service.AuthenticationService;
import com.contacttx.userservice.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(
            UserRegistrationService registrationService,
            AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegisterUserRequest request) {
        RegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
