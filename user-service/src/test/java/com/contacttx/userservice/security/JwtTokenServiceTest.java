package com.contacttx.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private KeyPair signingKeyPair;
    private JwtProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        signingKeyPair = generateKeyPair();
        properties = new JwtProperties();
        properties.setAccessTokenExpiry(Duration.ofMinutes(30));
    }

    @Test
    void generatesAValidTokenWithTheRequiredClaims() {
        JwtTokenService tokenService = new JwtTokenService(
                encoder(signingKeyPair), properties);
        JwtDecoder decoder = decoder(signingKeyPair);

        Jwt jwt = decoder.decode(tokenService.generateAccessToken(42L, "alex@example.com"));

        assertEquals("42", jwt.getSubject());
        assertEquals("alex@example.com", jwt.getClaimAsString("email"));
        assertEquals(1800L,
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        assertEquals(1800L, tokenService.getAccessTokenExpirySeconds());
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        JwtTokenService tokenService = new JwtTokenService(
                encoder(signingKeyPair),
                properties,
                Clock.fixed(oneHourAgo, ZoneOffset.UTC));
        JwtDecoder decoder = decoder(signingKeyPair);

        String expiredToken = tokenService.generateAccessToken(42L, "alex@example.com");

        assertThrows(JwtException.class, () -> decoder.decode(expiredToken));
    }

    @Test
    void rejectsAMalformedToken() {
        JwtDecoder decoder = decoder(signingKeyPair);

        assertThrows(JwtException.class, () -> decoder.decode("not-a-jwt"));
    }

    @Test
    void rejectsATokenSignedByAnotherPrivateKey() throws Exception {
        JwtTokenService tokenService = new JwtTokenService(
                encoder(signingKeyPair), properties);
        JwtDecoder decoderUsingAnotherPublicKey = decoder(generateKeyPair());

        String token = tokenService.generateAccessToken(42L, "alex@example.com");

        assertThrows(JwtException.class,
                () -> decoderUsingAnotherPublicKey.decode(token));
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private JwtEncoder encoder(KeyPair keyPair) {
        return NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()).build();
    }

    private JwtDecoder decoder(KeyPair keyPair) {
        return NimbusJwtDecoder.withPublicKey(
                (RSAPublicKey) keyPair.getPublic()).build();
    }
}
