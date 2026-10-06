package com.contacttx.userservice.security;

import com.contacttx.userservice.exception.InvalidAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new InvalidAuthenticationException();
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidAuthenticationException();
        }
    }
}
