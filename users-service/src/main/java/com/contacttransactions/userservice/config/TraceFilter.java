package com.contacttransactions.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_TRACE = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");
    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String supplied = request.getHeader(HEADER);
        String traceId = supplied != null && SAFE_TRACE.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        request.setAttribute("traceId", traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            Object userId = request.getAttribute("safeUserId");
            log.info("Request completed method={} route={} status={} durationMs={} userId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                    userId == null ? "anonymous" : userId);
            MDC.remove("traceId");
        }
    }
}
