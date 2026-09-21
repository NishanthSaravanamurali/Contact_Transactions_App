package com.contacttx.contactservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalServiceTokenFilterTest {

    private static final String TOKEN = "test-internal-token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAnInternalRequestWithTheConfiguredToken() throws Exception {
        InternalServiceTokenFilter filter = filterWithToken(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/v1/contacts/payment-eligibility");
        request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertEquals("internal-service", authentication.get().getPrincipal());
        assertEquals(
                InternalServiceTokenFilter.INTERNAL_AUTHORITY,
                authentication.get().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void doesNotAuthenticateAnInternalRequestWithTheWrongToken() throws Exception {
        InternalServiceTokenFilter filter = filterWithToken(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/v1/contacts/payment-eligibility");
        request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, "wrong-token");
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertNull(authentication.get());
    }

    @Test
    void ignoresTokensOnPublicPaths() throws Exception {
        InternalServiceTokenFilter filter = filterWithToken(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contacts");
        request.addHeader(InternalServiceTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertNull(authentication.get());
    }

    private InternalServiceTokenFilter filterWithToken(String token) {
        InternalServiceProperties properties = new InternalServiceProperties();
        properties.setServiceToken(token);
        return new InternalServiceTokenFilter(properties);
    }
}
