package com.petshop.gateway_service.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting do gateway (SDD-022) sobre o {@code RedisRateLimiter} do
 * Spring Cloud Gateway (token bucket no Redis), aplicado a todas as rotas.
 *
 * <p>Por que um {@link GlobalFilter} e não o filtro {@code RequestRateLimiter}
 * nativo por rota: os grupos de limite cruzam as rotas (leitura pública cai em
 * {@code product-registration} e {@code booking-service}; cadastro em
 * {@code customer-management}) e dependem do método HTTP, e o filtro nativo
 * responde o 429 sem corpo nem {@code Retry-After}. Aqui o grupo é escolhido
 * por método + path, o balde é o do {@code RedisRateLimiter} (headers
 * {@code X-RateLimit-*} incluídos) e o 429 sai em {@link ProblemDetail}, no
 * mesmo formato do filtro de login do {@code customer-management}.
 *
 * <p>Redis fora do ar → fail-open: a requisição passa e é registrado um
 * {@code WARN} (sem repetir a cada requisição).
 *
 * <p>Só roda em requisição roteada — {@code /actuator/**} é servido pelo
 * próprio gateway e nunca chega aqui; {@code excluded-paths} cobre o resto
 * (Swagger) explicitamente.
 */
@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    static final String PUBLIC_READ = "public-read";
    static final String REGISTRATION = "registration";
    static final String AUTHENTICATED = "authenticated";

    // Antes do roteamento (RouteToRequestUrlFilter = 10000, NettyRoutingFilter
    // = LOWEST_PRECEDENCE): requisição barrada nem chega ao downstream.
    private static final int ORDER = -100;
    private static final Duration FAIL_OPEN_WARN_INTERVAL = Duration.ofMinutes(1);
    // RedisRateLimiter devolve "permitido" com X-RateLimit-Remaining = -1
    // quando a chamada ao Redis falha (fail-open nativo, logado só por ele).
    private static final String FAIL_OPEN_REMAINING = "-1";

    private static final Logger log = LoggerFactory.getLogger(RateLimitGatewayFilter.class);

    private final RedisRateLimiter redisRateLimiter;
    private final ClientKeyResolver keyResolver;
    private final JsonMapper jsonMapper = new JsonMapper();
    private final List<PathPattern> excludedPaths;
    private final GroupMatcher publicRead;
    private final GroupMatcher registration;
    private final RateLimitProperties.Group authenticated;

    private final AtomicBoolean failingOpen = new AtomicBoolean(false);
    private final AtomicLong lastFailOpenWarn = new AtomicLong(0);

    public RateLimitGatewayFilter(RedisRateLimiter redisRateLimiter,
                                  ClientKeyResolver keyResolver,
                                  RateLimitProperties properties) {
        this.redisRateLimiter = redisRateLimiter;
        this.keyResolver = keyResolver;
        this.excludedPaths = parse(properties.excludedPaths());
        this.publicRead = new GroupMatcher(properties.publicRead());
        this.registration = new GroupMatcher(properties.registration());
        this.authenticated = properties.authenticated();

        // O RedisRateLimiter procura a config pelo "routeId" recebido em
        // isAllowed; aqui esse id é o nome do grupo. Ele também entra na
        // chave do Redis, então cada grupo tem baldes independentes.
        redisRateLimiter.getConfig().put(PUBLIC_READ, toConfig(properties.publicRead()));
        redisRateLimiter.getConfig().put(REGISTRATION, toConfig(properties.registration()));
        redisRateLimiter.getConfig().put(AUTHENTICATED, toConfig(authenticated));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        PathContainer path = request.getPath().pathWithinApplication();
        if (matchesAny(excludedPaths, path)) {
            return chain.filter(exchange);
        }

        String method = request.getMethod().name();
        if (publicRead.matches(method, path)) {
            return limit(exchange, chain, PUBLIC_READ, publicRead.group(), keyResolver.resolveByIp(exchange));
        }
        if (registration.matches(method, path)) {
            return limit(exchange, chain, REGISTRATION, registration.group(), keyResolver.resolveByIp(exchange));
        }
        return limit(exchange, chain, AUTHENTICATED, authenticated, keyResolver.resolve(exchange));
    }

    private Mono<Void> limit(ServerWebExchange exchange, GatewayFilterChain chain,
                             String groupId, RateLimitProperties.Group group, Mono<String> key) {
        return key
                .flatMap(id -> Mono.defer(() -> redisRateLimiter.isAllowed(groupId, id)))
                .onErrorResume(ex -> {
                    // Rede de segurança: o RedisRateLimiter já trata as falhas
                    // do Redis, mas nada aqui pode virar 500 para o cliente.
                    // Resposta marcada como fail-open (Remaining = -1), igual à do
                    // RedisRateLimiter: senão o flatMap abaixo trataria como
                    // "Redis voltou" e o WARN/INFO se repetiria a cada requisição.
                    warnFailOpen(ex);
                    return Mono.just(new RateLimiter.Response(true,
                            Map.of(redisRateLimiter.getRemainingHeader(), FAIL_OPEN_REMAINING)));
                })
                .flatMap(response -> {
                    ServerHttpResponse httpResponse = exchange.getResponse();

                    String remaining = response.getHeaders().get(redisRateLimiter.getRemainingHeader());
                    if (FAIL_OPEN_REMAINING.equals(remaining)) {
                        // Sem limite aplicado: não expõe X-RateLimit-* (Remaining = -1) ao cliente.
                        warnFailOpen(null);
                    } else {
                        response.getHeaders().forEach(httpResponse.getHeaders()::add);
                        recovered();
                    }

                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }
                    return tooManyRequests(httpResponse, retryAfterSeconds(group, remaining));
                });
    }

    /**
     * Segundos até o balde ter fichas suficientes para mais uma requisição:
     * (fichas que faltam) / (fichas por segundo), arredondado para cima.
     */
    static long retryAfterSeconds(RateLimitProperties.Group group, String remainingHeader) {
        long remaining = 0;
        if (remainingHeader != null) {
            try {
                remaining = Math.max(0, Long.parseLong(remainingHeader));
            } catch (NumberFormatException ignored) {
                // sem o header, considera o balde vazio
            }
        }
        long missing = Math.max(1, group.requestedTokens() - remaining);
        int rate = Math.max(1, group.replenishRate());
        return Math.max(1, (missing + rate - 1) / rate);
    }

    private Mono<Void> tooManyRequests(ServerHttpResponse response, long retryAfterSeconds) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisições. Aguarde " + retryAfterSeconds + "s antes de tentar novamente.");
        problem.setTitle("Too many requests");

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        byte[] body = jsonMapper.writeValueAsBytes(problem);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private void warnFailOpen(Throwable cause) {
        long now = System.currentTimeMillis();
        long last = lastFailOpenWarn.get();
        boolean firstFailure = failingOpen.compareAndSet(false, true);
        if ((firstFailure || now - last >= FAIL_OPEN_WARN_INTERVAL.toMillis())
                && lastFailOpenWarn.compareAndSet(last, now)) {
            if (cause != null) {
                log.warn("Rate limit em fail-open: erro ao consultar o Redis, requisições passando sem limite", cause);
            } else {
                log.warn("Rate limit em fail-open: Redis indisponível, requisições passando sem limite");
            }
        }
    }

    private void recovered() {
        if (failingOpen.compareAndSet(true, false)) {
            log.info("Rate limit: Redis respondeu de novo, limite voltou a ser aplicado");
        }
    }

    private static RedisRateLimiter.Config toConfig(RateLimitProperties.Group group) {
        return new RedisRateLimiter.Config()
                .setReplenishRate(group.replenishRate())
                .setBurstCapacity(group.burstCapacity())
                .setRequestedTokens(group.requestedTokens());
    }

    private static List<PathPattern> parse(List<String> patterns) {
        return patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    private static boolean matchesAny(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(p -> p.matches(path));
    }

    private record GroupMatcher(RateLimitProperties.Group group, List<PathPattern> paths) {

        GroupMatcher(RateLimitProperties.Group group) {
            this(group, parse(group.paths()));
        }

        boolean matches(String method, PathContainer path) {
            boolean methodMatches = group.methods().isEmpty()
                    || group.methods().stream().anyMatch(m -> m.equalsIgnoreCase(method));
            return methodMatches && matchesAny(paths, path);
        }
    }
}
