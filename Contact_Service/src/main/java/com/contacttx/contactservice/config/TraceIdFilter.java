package com.contacttx.contactservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE =
            TraceIdFilter.class.getName() + ".traceId";

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Pattern SAFE_TRACE_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        long startedAt = System.nanoTime();

        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            LOGGER.info(
                    "requestPath={} status={} traceId={} durationMs={}",
                    request.getRequestURI(),
                    response.getStatus(),
                    traceId,
                    durationMillis
            );
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String incomingTraceId) {
        if (incomingTraceId != null) {
            String trimmedTraceId = incomingTraceId.trim();
            if (SAFE_TRACE_ID_PATTERN.matcher(trimmedTraceId).matches()) {
                return trimmedTraceId;
            }
        }

        return UUID.randomUUID().toString();
    }
}
