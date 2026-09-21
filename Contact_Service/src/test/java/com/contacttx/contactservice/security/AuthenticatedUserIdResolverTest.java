package com.contacttx.contactservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedUserIdResolverTest {

    private final AuthenticatedUserIdResolver resolver = new AuthenticatedUserIdResolver();

    @Test
    void resolvesPositiveNumericJwtSubject() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject("101"));

        assertEquals(101L, resolver.resolve(authentication));
    }

    @Test
    void rejectsNonNumericJwtSubject() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject("user-101"));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(authentication));
    }

    @Test
    void rejectsNonPositiveJwtSubject() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject("0"));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(authentication));
    }

    private Jwt jwtWithSubject(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
