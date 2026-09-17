package com.contacttx.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdWebFilter implements WebFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = validOrGeneratedTraceId(
                exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER));

        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request -> request.headers(headers ->
                        headers.set(TRACE_ID_HEADER, traceId)))
                .build();

        tracedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(tracedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String validOrGeneratedTraceId(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // Invalid external values are replaced instead of being propagated.
            }
        }
        return UUID.randomUUID().toString();
    }
}
