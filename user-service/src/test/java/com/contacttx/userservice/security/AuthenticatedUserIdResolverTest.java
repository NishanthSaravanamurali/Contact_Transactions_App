package com.contacttx.userservice.security;

import com.contacttx.userservice.exception.InvalidAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedUserIdResolverTest {

    private final AuthenticatedUserIdResolver resolver = new AuthenticatedUserIdResolver();

    @Test
    void resolvesTheOracleUserIdFromTheJwtSubject() {
        Jwt jwt = jwtWithSubject("42");

        assertEquals(42L, resolver.resolve(jwt));
    }

    @Test
    void rejectsANonNumericJwtSubject() {
        Jwt jwt = jwtWithSubject("another-user");

        assertThrows(InvalidAuthenticationException.class, () -> resolver.resolve(jwt));
    }

    private Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .build();
    }
}
