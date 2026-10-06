package com.oracle.notificationservice.config;

import com.oracle.notificationservice.security.RsaPublicKeyParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.public-key}") String publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(RsaPublicKeyParser.parse(publicKey)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<Instant>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("sub", subject -> {
                    try { return subject != null && Long.parseLong(subject) > 0; }
                    catch (NumberFormatException exception) { return false; }
                })));
        return decoder;
    }
}
