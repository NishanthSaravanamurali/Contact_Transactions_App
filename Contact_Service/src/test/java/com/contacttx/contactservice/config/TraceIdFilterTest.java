package com.contacttx.contactservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceIdFilterTest {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void preservesSafeIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contacts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "gateway-trace_123.abc");
        AtomicReference<String> downstreamMdcValue = new AtomicReference<>();

        FilterChain filterChain = (servletRequest, servletResponse) ->
                downstreamMdcValue.set(MDC.get(TRACE_ID_MDC_KEY));

        traceIdFilter.doFilter(request, response, filterChain);

        assertEquals(
                "gateway-trace_123.abc",
                request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
        assertEquals(
                "gateway-trace_123.abc",
                response.getHeader(TraceIdFilter.TRACE_ID_HEADER)
        );
        assertEquals("gateway-trace_123.abc", downstreamMdcValue.get());
        assertNull(MDC.get(TRACE_ID_MDC_KEY));
    }

    @Test
    void generatesTraceIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertDoesNotThrow(() -> UUID.fromString(generatedTraceId));
        assertEquals(
                generatedTraceId,
                request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
        assertNull(MDC.get(TRACE_ID_MDC_KEY));
    }

    @Test
    void replacesUnsafeIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contacts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String unsafeTraceId = "unsafe trace\r\nforged-log-entry";
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, unsafeTraceId);

        traceIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotEquals(unsafeTraceId, generatedTraceId);
        assertDoesNotThrow(() -> UUID.fromString(generatedTraceId));
        assertNull(MDC.get(TRACE_ID_MDC_KEY));
    }

    @Test
    void clearsLoggingContextWhenDownstreamProcessingFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/contacts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "request-that-fails");

        assertThrows(
                IllegalStateException.class,
                () -> traceIdFilter.doFilter(
                        request,
                        response,
                        (servletRequest, servletResponse) -> {
                            throw new IllegalStateException("simulated downstream failure");
                        }
                )
        );

        assertEquals(
                "request-that-fails",
                response.getHeader(TraceIdFilter.TRACE_ID_HEADER)
        );
        assertNull(MDC.get(TRACE_ID_MDC_KEY));
    }
}
