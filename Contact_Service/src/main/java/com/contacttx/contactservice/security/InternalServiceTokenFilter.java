package com.contacttx.contactservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    public static final String INTERNAL_AUTHORITY = "INTERNAL_SERVICE";

    private final InternalServiceProperties properties;

    public InternalServiceTokenFilter(InternalServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (tokensMatch(properties.getServiceToken(), request.getHeader(INTERNAL_TOKEN_HEADER))) {
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            "internal-service",
                            null,
                            List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY)));
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
        }

        filterChain.doFilter(request, response);
    }

    private boolean tokensMatch(String expectedToken, String suppliedToken) {
        if (expectedToken == null || expectedToken.isBlank()
                || suppliedToken == null || suppliedToken.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
