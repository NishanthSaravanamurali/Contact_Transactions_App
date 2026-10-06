package com.contacttx.userservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalServiceTokenFilterTest {

    private InternalServiceTokenFilter filter;

    @BeforeEach
    void setUp() {
        InternalServiceProperties properties = new InternalServiceProperties();
        properties.setServiceToken("test-internal-token");
        filter = new InternalServiceTokenFilter(properties);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAValidInternalServiceToken() throws Exception {
        Authentication authentication = filterAuthentication("test-internal-token");

        assertNotNull(authentication);
        assertEquals("internal-service", authentication.getPrincipal());
        assertEquals(
                InternalServiceTokenFilter.INTERNAL_AUTHORITY,
                authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void doesNotAuthenticateAnIncorrectInternalServiceToken() throws Exception {
        assertNull(filterAuthentication("incorrect-token"));
    }

    @Test
    void doesNotAuthenticateAMissingInternalServiceToken() throws Exception {
        assertNull(filterAuthentication(null));
    }

    private Authentication filterAuthentication(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/internal/v1/users/42/status");
        if (token != null) {
            request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> capturedAuthentication = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                capturedAuthentication.set(
                        SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(request, response, chain);

        return capturedAuthentication.get();
    }
}
