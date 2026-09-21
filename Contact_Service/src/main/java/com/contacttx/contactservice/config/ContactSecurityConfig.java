package com.contacttx.contactservice.config;

import com.contacttx.contactservice.security.ContactJwtProperties;
import com.contacttx.contactservice.security.InternalServiceProperties;
import com.contacttx.contactservice.security.InternalServiceTokenFilter;
import com.contacttx.contactservice.security.RsaPublicKeyParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ContactJwtProperties.class, InternalServiceProperties.class})
public class ContactSecurityConfig {

    @Bean
    @Lazy
    public JwtDecoder jwtDecoder(ContactJwtProperties properties) {
        return NimbusJwtDecoder.withPublicKey(
                RsaPublicKeyParser.parse(properties.getPublicKey())
        ).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Lazy JwtDecoder jwtDecoder,
            InternalServiceTokenFilter internalServiceTokenFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/internal/**")
                        .hasAuthority(InternalServiceTokenFilter.INTERNAL_AUTHORITY)
                        .requestMatchers("/api/v1/contacts/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .addFilterBefore(
                        internalServiceTokenFilter,
                        BearerTokenAuthenticationFilter.class)
                .build();
    }
}
