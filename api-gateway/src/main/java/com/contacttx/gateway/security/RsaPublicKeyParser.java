package com.contacttx.gateway.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaPublicKeyParser {

    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private RsaPublicKeyParser() {
    }

    public static RSAPublicKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT public key is not configured");
        }

        String normalizedPem = pem.replace("\\n", "\n").trim();
        if (!normalizedPem.contains(PUBLIC_KEY_BEGIN)
                || !normalizedPem.contains(PUBLIC_KEY_END)) {
            throw new IllegalStateException(
                    "JWT public key does not contain the expected PEM markers");
        }

        String encodedKey = normalizedPem
                .replace(PUBLIC_KEY_BEGIN, "")
                .replace(PUBLIC_KEY_END, "")
                .replaceAll("\\s", "");

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT public key must be a valid X.509 RSA PEM value", exception);
        }
    }
}
