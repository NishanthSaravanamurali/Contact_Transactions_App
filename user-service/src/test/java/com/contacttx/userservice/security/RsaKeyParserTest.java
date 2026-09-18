package com.contacttx.userservice.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsaKeyParserTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateTemporaryKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void parsesPkcs8PrivateAndX509PublicPemValues() {
        String privatePem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());

        RSAPrivateKey privateKey = RsaKeyParser.parsePrivateKey(privatePem);
        RSAPublicKey publicKey = RsaKeyParser.parsePublicKey(publicPem);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), privateKey.getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), publicKey.getEncoded());
    }

    @Test
    void acceptsLiteralNewlineSequencesUsedByEnvironmentVariables() {
        String escapedPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded())
                .replace("\n", "\\n");

        RSAPublicKey publicKey = RsaKeyParser.parsePublicKey(escapedPem);

        assertArrayEquals(keyPair.getPublic().getEncoded(), publicKey.getEncoded());
    }

    @Test
    void rejectsMissingKeyMaterialWithoutEchoingAKey() {
        assertThrows(IllegalStateException.class,
                () -> RsaKeyParser.parsePrivateKey(" "));
    }

    private static String toPem(String type, byte[] encodedKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encodedKey);
        return "-----BEGIN " + type + "-----\n"
                + base64
                + "\n-----END " + type + "-----";
    }
}
