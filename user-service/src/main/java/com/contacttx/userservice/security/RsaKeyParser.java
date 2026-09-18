package com.contacttx.userservice.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyParser {

    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private RsaKeyParser() {
    }

    public static RSAPrivateKey parsePrivateKey(String pem) {
        byte[] keyBytes = decodePem(pem, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END, "private");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT private key must be a valid PKCS#8 RSA PEM value", exception);
        }
    }

    public static RSAPublicKey parsePublicKey(String pem) {
        byte[] keyBytes = decodePem(pem, PUBLIC_KEY_BEGIN, PUBLIC_KEY_END, "public");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT public key must be a valid X.509 RSA PEM value", exception);
        }
    }

    private static byte[] decodePem(
            String pem,
            String beginMarker,
            String endMarker,
            String keyType) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT " + keyType + " key is not configured");
        }

        String normalizedPem = pem.replace("\\n", "\n").trim();
        if (!normalizedPem.contains(beginMarker) || !normalizedPem.contains(endMarker)) {
            throw new IllegalStateException(
                    "JWT " + keyType + " key does not contain the expected PEM markers");
        }

        String encodedKey = normalizedPem
                .replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");

        try {
            return Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT " + keyType + " key contains invalid Base64 data", exception);
        }
    }
}
