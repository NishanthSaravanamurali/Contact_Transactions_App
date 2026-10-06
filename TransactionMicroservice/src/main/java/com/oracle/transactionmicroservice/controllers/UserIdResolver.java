package com.oracle.transactionmicroservice.controllers;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserIdResolver {

    public Long resolve(Jwt jwt) {
        try {
            return Long.valueOf(Objects.requireNonNull(jwt.getSubject()));
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("JWT subject must be a numeric user ID.");
        }
    }
}