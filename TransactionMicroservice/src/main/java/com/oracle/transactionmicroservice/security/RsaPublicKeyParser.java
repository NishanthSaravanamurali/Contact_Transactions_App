package com.oracle.transactionmicroservice.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaPublicKeyParser {

    private static final String BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String END = "-----END PUBLIC KEY-----";

    private RsaPublicKeyParser() {
    }

    public static RSAPublicKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT public key is not configured.");
        }

        String normalized = pem.replace("\\n", "\n").trim();

        if (!normalized.contains(BEGIN) || !normalized.contains(END)) {
            throw new IllegalStateException(
                    "JWT public key does not contain valid PEM markers."
            );
        }

        String encoded = normalized
                .replace(BEGIN, "")
                .replace(END, "")
                .replaceAll("\\s", "");

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);

            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT public key must be a valid X.509 RSA PEM value.",
                    exception
            );
        }
    }
}