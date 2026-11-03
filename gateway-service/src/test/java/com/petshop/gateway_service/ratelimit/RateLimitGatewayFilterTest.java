package com.petshop.gateway_service.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitGatewayFilterTest {

    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final InetSocketAddress CLIENT = new InetSocketAddress("10.0.0.1", 54321);

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitGatewayFilter filter;

    @BeforeEach
    void setUp() {
        when(redisRateLimiter.getConfig()).thenReturn(new HashMap<>());
        var properties = new RateLimitProperties(
                List.of("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"),
                new RateLimitProperties.Group(List.of("GET"), List.of("/api/v1/products/**", "/api/v1/bookings/available-slots"), 20, 40, 1),
                new RateLimitProperties.Group(List.of("POST"), List.of("/api/v1/customers/register/**"), 1, 60, 12),
                new RateLimitProperties.Group(null, null, 10, 20, 1));
        filter = new RateLimitGatewayFilter(redisRateLimiter, new ClientKeyResolver(new JwtSubjectResolver(null)), properties);
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).remoteAddress(CLIENT));
    }

    private void givenLimiterResponds(boolean allowed, String remaining) {
        when(redisRateLimiter.getRemainingHeader()).thenReturn(REMAINING_HEADER);
        when(redisRateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(allowed, Map.of(REMAINING_HEADER, remaining))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs/booking", "/swagger-ui/index.html", "/swagger-ui.html"})
    @DisplayName("Deve deixar passar sem consultar o limite quando o path estiver excluído")
    void shouldSkipLimitWhenPathIsExcluded(String path) {
        var exchange = exchange(HttpMethod.GET, path);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(redisRateLimiter, never()).isAllowed(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/products/list", "/api/v1/bookings/available-slots"})
    @DisplayName("Deve aplicar o grupo de leitura pública por IP quando for GET de rota pública")
    void shouldUsePublicReadGroupByIpWhenGetPublicRoute(String path) {
        var exchange = exchange(HttpMethod.GET, path);
        givenLimiterResponds(true, "39");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(redisRateLimiter).isAllowed("public-read", "ip:10.0.0.1");
    }

    @Test
    @DisplayName("Deve aplicar o grupo de cadastro por IP quando for POST de cadastro")
    void shouldUseRegistrationGroupWhenPostRegister() {
        var exchange = exchange(HttpMethod.POST, "/api/v1/customers/register/customer");
        givenLimiterResponds(true, "48");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(redisRateLimiter).isAllowed("registration", "ip:10.0.0.1");
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/v1/bookings/list", "POST, /api/v1/products/create", "GET, /api/v1/customers/register/customer"})
    @DisplayName("Deve aplicar o grupo autenticado quando a requisição não casar com os grupos públicos")
    void shouldUseAuthenticatedGroupForOtherRoutes(String method, String path) {
        var exchange = exchange(HttpMethod.valueOf(method), path);
        givenLimiterResponds(true, "19");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(redisRateLimiter).isAllowed("authenticated", "ip:10.0.0.1");
    }

    @Test
    @DisplayName("Deve responder 429 com ProblemDetail e Retry-After quando o limite estourar")
    void shouldReturn429ProblemDetailWithRetryAfterWhenDenied() {
        var exchange = exchange(HttpMethod.POST, "/api/v1/customers/register/customer");
        givenLimiterResponds(false, "0");

        filter.filter(exchange, chain).block();

        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
        assertThat(response.getHeaders().getFirst(REMAINING_HEADER)).isEqualTo("0");
        assertThat(response.getBodyAsString().block())
                .contains("\"title\":\"Too many requests\"")
                .contains("\"status\":429")
                .contains("Aguarde 12s");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Deve repassar os headers do limite quando a requisição for permitida")
    void shouldCopyRateLimitHeadersWhenAllowed() {
        var exchange = exchange(HttpMethod.GET, "/api/v1/products/list");
        givenLimiterResponds(true, "39");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(REMAINING_HEADER)).isEqualTo("39");
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Deve deixar passar quando o Redis estiver fora e o limitador sinalizar fail-open")
    void shouldFailOpenWhenRemainingIsMinusOne() {
        var exchange = exchange(HttpMethod.GET, "/api/v1/products/list");
        givenLimiterResponds(true, "-1");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
        filter.filter(exchange, chain).block();

        verify(chain, org.mockito.Mockito.times(2)).filter(exchange);
    }

    @Test
    @DisplayName("Deve voltar a aplicar o limite quando o Redis responder de novo após o fail-open")
    void shouldApplyLimitAgainWhenRedisRecovers() {
        var exchange = exchange(HttpMethod.GET, "/api/v1/products/list");
        when(redisRateLimiter.getRemainingHeader()).thenReturn(REMAINING_HEADER);
        when(redisRateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, Map.of(REMAINING_HEADER, "-1"))))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of(REMAINING_HEADER, "0"))));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
        var secondExchange = exchange(HttpMethod.GET, "/api/v1/products/list");
        filter.filter(secondExchange, chain).block();

        assertThat(secondExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Deve deixar passar quando o limitador lançar erro")
    void shouldFailOpenWhenLimiterThrows() {
        var exchange = exchange(HttpMethod.GET, "/api/v1/products/list");
        when(redisRateLimiter.getRemainingHeader()).thenReturn(REMAINING_HEADER);
        when(redisRateLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "12, 1, NULL, 12",
            "12, 1, abc, 12",
            "12, 1, 0, 12",
            "12, 1, 5, 7",
            "12, 1, 50, 1",
            "1, 20, 0, 1",
            "30, 20, 0, 2"
    })
    @DisplayName("Deve calcular o Retry-After arredondando para cima e com mínimo de 1 segundo")
    void shouldComputeRetryAfterSeconds(int requestedTokens, int replenishRate, String remaining, long expected) {
        var group = new RateLimitProperties.Group(null, null, replenishRate, 60, requestedTokens);

        var seconds = RateLimitGatewayFilter.retryAfterSeconds(group, remaining);

        assertThat(seconds).isEqualTo(expected);
    }
}
