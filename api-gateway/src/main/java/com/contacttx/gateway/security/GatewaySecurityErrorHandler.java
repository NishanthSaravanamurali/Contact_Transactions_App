package com.contacttx.gateway.security;

import com.contacttx.gateway.config.TraceIdWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception) {
        return writeError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required");
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception) {
        return writeError(
                exchange,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Operation is not permitted");
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        String traceId = exchange.getRequest().getHeaders()
                .getFirst(TraceIdWebFilter.TRACE_ID_HEADER);
        String body = """
                {"timestamp":"%s","traceId":"%s","status":%d,"code":"%s","message":"%s","fieldErrors":{}}
                """.formatted(
                Instant.now(),
                traceId,
                status.value(),
                code,
                message).trim();

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
