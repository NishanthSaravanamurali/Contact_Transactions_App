package com.contacttx.gateway;

import com.contacttx.gateway.config.GatewaySecurityConfig;
import com.contacttx.gateway.security.GatewaySecurityErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {GatewaySecurityConfig.class, GatewaySecurityErrorHandler.class,
        NotificationGatewayTest.Support.class})
class NotificationGatewayTest {
    @Autowired ApplicationContext context;
    @MockitoBean ReactiveJwtDecoder decoder;
    WebTestClient client;

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class Support {
        @Bean NotificationEndpoints endpoints() { return new NotificationEndpoints(); }
    }

    @RestController
    static class NotificationEndpoints {
        @GetMapping({"/api/v1/notifications", "/api/v1/notifications/stream"})
        String history() { return "allowed"; }
        @PatchMapping("/api/v1/notifications/read-all")
        String readAll() { return "allowed"; }
    }

    @BeforeEach void setup() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test void notificationRoutesRequireAuthentication() {
        client.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/notifications/stream").exchange().expectStatus().isUnauthorized();
        client.patch().uri("/api/v1/notifications/read-all").exchange().expectStatus().isUnauthorized();
    }

    @Test void authenticatedNotificationRequestsAreAllowedButInternalRoutesStayBlocked() {
        WebTestClient authenticated = client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("2")));
        authenticated.get().uri("/api/v1/notifications").exchange().expectStatus().isOk();
        authenticated.get().uri("/api/v1/notifications/stream").exchange().expectStatus().isOk();
        authenticated.patch().uri("/api/v1/notifications/read-all").exchange().expectStatus().isOk();
        authenticated.get().uri("/internal/v1/users/1/status").exchange().expectStatus().isForbidden();
    }

    @Test void gatewayConfigurationRoutesBasePathAndChildrenToNotificationService() throws Exception {
        Properties properties = new Properties();
        try (var stream = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(stream);
        }
        String prefix = "spring.cloud.gateway.server.webflux.routes[4].";
        assertThat(properties.getProperty(prefix + "uri")).isEqualTo("lb://NOTIFICATION-SERVICE");
        assertThat(properties.getProperty(prefix + "predicates[0]"))
                .isEqualTo("Path=/api/v1/notifications,/api/v1/notifications/**");
    }
}
