package com.contacttx.contactservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new IllegalStateException("An authenticated JWT is required");
        }

        String subject = jwtAuthentication.getToken().getSubject();
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException("User ID must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("JWT subject must be a positive numeric user ID", exception);
        }
    }
}
