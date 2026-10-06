package com.contacttx.userservice.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this(jwtEncoder, jwtProperties, Clock.systemUTC());
    }

    JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String generateAccessToken(Long userId, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required to generate an access token");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required to generate an access token");
        }

        Duration tokenLifetime = jwtProperties.getAccessTokenExpiry();
        if (tokenLifetime == null || tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalStateException("JWT access-token expiry must be positive");
        }

        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(tokenLifetime))
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenExpirySeconds() {
        return jwtProperties.getAccessTokenExpiry().toSeconds();
    }
}
