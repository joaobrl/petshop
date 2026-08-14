package com.petshop.commons.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void generatesNewCorrelationIdWhenHeaderIsAbsent() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER), captor.capture());
        assertThat(captor.getValue()).isNotBlank();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void propagatesCorrelationIdAlreadyPresentInRequestHeader() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("existing-correlation-id");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(CorrelationIdFilter.HEADER, "existing-correlation-id");
    }

    @Test
    void putsCorrelationIdInMdcDuringTheFilterChainCall() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("mdc-check-id");
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("mdc-check-id");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void clearsMdcAfterFilterChainCompletes() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("cleanup-id");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void treatsBlankHeaderAsAbsentAndGeneratesNewId() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER), captor.capture());
        assertThat(captor.getValue()).isNotBlank().isNotEqualTo("   ");
    }
}
