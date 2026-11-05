package com.petshop.gateway_service.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Extrai o {@code sub} de um {@code Authorization: Bearer} só se o token for válido
 * para o Keycloak do projeto (SDD-022, SDD-024): assinatura RS256 conferida com a chave
 * pública do JWKS, {@code iss} do realm e {@code exp}. Serve apenas para escolher a
 * chave do rate limit — sem validar a assinatura, qualquer um forjaria um {@code sub}
 * novo a cada requisição e nunca seria limitado.
 *
 * <p>Por que JWKS e não "só decodificar": é o único jeito de a chave por usuário não ser
 * forjável sem o gateway guardar segredo nenhum (a chave é pública). O JWKS fica em
 * cache no {@link NimbusReactiveJwtDecoder} e é rebuscado quando chega um {@code kid}
 * novo (rotação sem restart). O {@code aud} não é checado: o gateway não é audience de
 * nenhum token, e para limitar basta saber que o token foi emitido pelo realm.
 *
 * <p>Não autentica nada: token ausente, inválido, expirado ou Keycloak fora do ar só
 * faz a chave cair para o IP. Quem rejeita a requisição continua sendo cada serviço.
 */
@Component
public class JwtSubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtSubjectResolver.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ReactiveJwtDecoder decoder;

    @Autowired
    public JwtSubjectResolver(@Value("${keycloak.jwk-set-uri:}") String jwkSetUri,
                              @Value("${keycloak.issuer-uri:}") String issuerUri) {
        this(buildDecoder(jwkSetUri, issuerUri));
    }

    JwtSubjectResolver(ReactiveJwtDecoder decoder) {
        this.decoder = decoder;
    }

    private static ReactiveJwtDecoder buildDecoder(String jwkSetUri, String issuerUri) {
        // Diferente dos serviços, o gateway não falha a subida sem Keycloak
        // configurado: ele não autentica ninguém, só perde a chave por usuário.
        if (jwkSetUri == null || jwkSetUri.isBlank() || issuerUri == null || issuerUri.isBlank()) {
            log.warn("Rate limit: keycloak.jwk-set-uri/issuer-uri não configurados — todas as requisições serão limitadas por IP");
            return null;
        }
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    /**
     * @return o {@code sub} do token, ou vazio se não houver Bearer ou se o token for
     *         inválido/expirado (nunca erro)
     */
    public Mono<String> resolveSubject(String authorizationHeader) {
        if (decoder == null || authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        return Mono.defer(() -> decoder.decode(token))
                .mapNotNull(jwt -> jwt.getSubject())
                .filter(sub -> !sub.isBlank())
                .onErrorResume(ex -> {
                    log.debug("Rate limit: Bearer não validado ({}), chave cai para o IP", ex.getMessage());
                    return Mono.empty();
                });
    }
}
