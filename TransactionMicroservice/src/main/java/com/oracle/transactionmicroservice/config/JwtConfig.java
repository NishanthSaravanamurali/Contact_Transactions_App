package com.oracle.transactionmicroservice.config;

import com.oracle.transactionmicroservice.security.RsaPublicKeyParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${security.jwt.public-key}") String publicKey
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(RsaPublicKeyParser.parse(publicKey))
                .build();

        decoder.setJwtValidator(JwtValidators.createDefault());

        return decoder;
    }
}