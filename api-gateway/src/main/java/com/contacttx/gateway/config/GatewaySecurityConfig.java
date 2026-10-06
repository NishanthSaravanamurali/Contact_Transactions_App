package com.contacttx.gateway.config;

import com.contacttx.gateway.security.GatewayJwtProperties;
import com.contacttx.gateway.security.GatewaySecurityErrorHandler;
import com.contacttx.gateway.security.RsaPublicKeyParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewaySecurityConfig {

    @Bean
    @Lazy
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewayJwtProperties properties) {
        RSAPublicKey publicKey = RsaPublicKeyParser.parse(properties.getPublicKey());
        return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            GatewaySecurityErrorHandler errorHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login")
                        .permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .pathMatchers("/api/v1/users/**").authenticated()
                        .pathMatchers("/api/v1/money/**").authenticated()
                        .pathMatchers("/api/v1/contacts/**").authenticated()
                        .pathMatchers("/api/v1/notifications", "/api/v1/notifications/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
