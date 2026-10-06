package com.contacttx.userservice.config;

import com.contacttx.userservice.security.JwtProperties;
import com.contacttx.userservice.security.JwtTokenService;
import com.contacttx.userservice.security.RsaKeyParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    @Lazy
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = RsaKeyParser.parsePublicKey(properties.getPublicKey());
        RSAPrivateKey privateKey = RsaKeyParser.parsePrivateKey(properties.getPrivateKey());
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
    }

    @Bean
    @Lazy
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        RSAPublicKey publicKey = RsaKeyParser.parsePublicKey(properties.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean
    @Lazy
    public JwtTokenService jwtTokenService(
            JwtEncoder jwtEncoder,
            JwtProperties properties) {
        return new JwtTokenService(jwtEncoder, properties);
    }
}
