package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.LoginRequest;
import com.contacttx.userservice.dto.response.LoginResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.InvalidCredentialsException;
import com.contacttx.userservice.repository.AppUserRepository;
import com.contacttx.userservice.security.JwtTokenService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthenticationService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Lazy JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtTokenService.generateAccessToken(
                user.getUserId(), user.getEmail());
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenService.getAccessTokenExpirySeconds());
    }
}
