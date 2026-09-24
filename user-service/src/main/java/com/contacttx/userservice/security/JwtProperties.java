package com.contacttx.userservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private Duration accessTokenExpiry = Duration.ofMinutes(30);
    private String privateKey;
    private String publicKey;

    public Duration getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public void setAccessTokenExpiry(Duration accessTokenExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
