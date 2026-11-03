package com.petshop.gateway_service.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Limites do rate limiting do gateway (SDD-022), lidos de
 * {@code gateway.rate-limit.*} no application.yaml.
 *
 * <p>Cada grupo é um token bucket do {@code RedisRateLimiter}: o balde
 * comporta {@code burstCapacity} fichas, recebe {@code replenishRate} fichas
 * por segundo e cada requisição consome {@code requestedTokens} fichas.
 *
 * @param excludedPaths paths que nunca passam pelo limite (actuator, Swagger)
 * @param publicRead    leitura pública, chave por IP
 * @param registration  cadastro de cliente, chave por IP
 * @param authenticated demais requisições roteadas, chave por {@code sub} do
 *                      JWT (IP quando anônimo/token inválido) — não usa
 *                      {@code methods}/{@code paths}, é o grupo padrão
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        List<String> excludedPaths,
        Group publicRead,
        Group registration,
        Group authenticated
) {

    public RateLimitProperties {
        excludedPaths = excludedPaths == null ? List.of() : List.copyOf(excludedPaths);
    }

    /**
     * @param methods métodos HTTP que entram no grupo (vazio = qualquer um)
     * @param paths   padrões de path (PathPattern) que entram no grupo
     */
    public record Group(
            List<String> methods,
            List<String> paths,
            int replenishRate,
            long burstCapacity,
            int requestedTokens
    ) {

        public Group {
            methods = methods == null ? List.of() : List.copyOf(methods);
            paths = paths == null ? List.of() : List.copyOf(paths);
            if (requestedTokens <= 0) {
                requestedTokens = 1;
            }
        }
    }
}
