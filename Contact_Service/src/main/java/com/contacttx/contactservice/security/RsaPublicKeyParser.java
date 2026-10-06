package com.contacttx.contactservice.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaPublicKeyParser {

    private RsaPublicKeyParser() {
    }

    public static RSAPublicKey parse(String pemValue) {
        if (pemValue == null || pemValue.isBlank()) {
            throw new IllegalStateException("JWT public key must be configured");
        }

        try {
            String normalizedPem = pemValue.replace("\\n", "\n");

            String base64Key = normalizedPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key is invalid", exception);
        }
    }
}
