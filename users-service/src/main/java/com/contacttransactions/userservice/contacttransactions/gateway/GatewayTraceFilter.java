package com.contacttransactions.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayTraceFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_TRACE = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");
    private static final Logger log = LoggerFactory.getLogger(GatewayTraceFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long started = System.nanoTime();
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String traceId = supplied != null && SAFE_TRACE.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, traceId)).build();
        exchange.getAttributes().put("traceId", traceId);
        exchange.getResponse().getHeaders().set(HEADER, traceId);
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> {
                    long durationMs = (System.nanoTime() - started) / 1_000_000;
                    log.info("Gateway request completed traceId={} method={} route={} status={} durationMs={}",
                            traceId, request.getMethod(), request.getURI().getPath(),
                            exchange.getResponse().getStatusCode(), durationMs);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
