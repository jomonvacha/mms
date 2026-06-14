package com.roots.mms.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void noIncomingHeader_generatesTraceId_andSetsResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isNotBlank();
    }

    @Test
    void xRequestIdHeader_propagatedAsTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_REQUEST_ID, "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isEqualTo("req-abc-123");
    }

    @Test
    void xTraceIdHeader_propagatedAsTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "trace-xyz-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isEqualTo("trace-xyz-456");
    }

    @Test
    void xRequestIdTakesPrecedenceOverXTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_REQUEST_ID, "req-wins");
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "trace-loses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(TraceIdFilter.HEADER_TRACE_ID)).isEqualTo("req-wins");
    }

    @Test
    void mdcIsCleanedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_REQUEST_ID, "cleanup-check");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    void filterChainIsAlwaysCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void generatedTraceId_is16CharactersUpperCase() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String traceId = response.getHeader(TraceIdFilter.HEADER_TRACE_ID);
        assertThat(traceId).hasSize(16);
        assertThat(traceId).isUpperCase();
        assertThat(traceId).matches("[0-9A-F]+");
    }
}
