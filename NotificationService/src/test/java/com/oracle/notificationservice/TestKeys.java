package com.oracle.notificationservice;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;

final class TestKeys {
    static final KeyPair PAIR = generate();

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    static String publicPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    static String token(String subject, Instant expiry) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(300)));
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) PAIR.getPrivate()));
        return jwt.serialize();
    }

    private TestKeys() {}
}
