package com.petshop.customermanagement.infrastructure.adapters.outbound.keycloak;

import com.petshop.commons.exception.BusinessException;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.DependencyUnavailableException;
import com.petshop.commons.feign.TokenPropagation;
import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Admin API do Keycloak (SDD-024 F3) via REST direto ({@link RestClient}), autenticada com o
 * client {@code customer-management-admin} (Client Credentials, token reaproveitado até
 * expirar pelo {@link TokenPropagation}). Usuários ficam no realm {@code petshop} com
 * username = e-mail e os atributos do User Profile {@code user_id}, {@code cpf},
 * {@code phone} e {@code type}, que viram os claims do token.
 */
@Slf4j
@Component
public class KeycloakAdminAdapterOut implements IdentityProviderPortOut {

    static final String ATTR_USER_ID = "user_id";
    static final String ATTR_CPF = "cpf";
    static final String ATTR_PHONE = "phone";
    static final String ATTR_TYPE = "type";
    static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private static final String DEPENDENCY = "keycloak";
    private static final String EMAIL_CONFLICT_MESSAGE = "Email já cadastrado";
    private static final String REJECTED_MESSAGE =
            "Dados recusados pelo provedor de identidade. Revise nome, e-mail e telefone.";
    private static final Set<String> APP_ROLES =
            Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final Supplier<String> authorizationHeader;
    private final String webClientId;
    private final String actionRedirectUri;

    @Autowired
    public KeycloakAdminAdapterOut(@Value("${keycloak.admin.base-url}") String baseUrl,
                                   @Value("${keycloak.admin.realm:petshop}") String realm,
                                   @Value("${keycloak.client.token-uri}") String tokenUri,
                                   @Value("${keycloak.client.id:customer-management-admin}") String clientId,
                                   @Value("${keycloak.client.secret:}") String clientSecret,
                                   @Value("${keycloak.admin.web-client-id:web}") String webClientId,
                                   @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this(buildRestClient(baseUrl, realm),
                TokenPropagation.clientCredentials(tokenUri, clientId, clientSecret)::mintServiceToken,
                webClientId, stripTrailingSlash(frontendBaseUrl) + "/");
    }

    KeycloakAdminAdapterOut(RestClient restClient, Supplier<String> authorizationHeader,
                            String webClientId, String actionRedirectUri) {
        this.restClient = restClient;
        this.authorizationHeader = authorizationHeader;
        this.webClientId = webClientId;
        this.actionRedirectUri = actionRedirectUri;
    }

    private static RestClient buildRestClient(String baseUrl, String realm) {
        // HTTP/1.1 explícito: o HttpClient do JDK tenta upgrade h2c em http:// por padrão.
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(stripTrailingSlash(baseUrl) + "/admin/realms/" + realm)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String createUser(IdentityUser user, String password) {
        var body = new LinkedHashMap<String, Object>();
        body.put("username", user.email().toLowerCase(Locale.ROOT));
        body.put("enabled", true);
        applyProfile(body, user);
        body.put("emailVerified", user.emailVerified());
        if (password != null) {
            body.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        }
        var response = call(() -> restClient.post().uri("/users")
                .headers(this::authorize)
                .body(body)
                .retrieve()
                .toBodilessEntity());
        var idpUserId = idFromLocation(response.getHeaders().getLocation());
        try {
            syncRole(idpUserId, user.role());
        } catch (RuntimeException e) {
            // Usuário sem papel não deve sobrar no realm: desfaz e propaga a falha.
            deleteQuietly(idpUserId);
            throw e;
        }
        log.info("Identity provider user created: userId={}", user.userId());
        return idpUserId;
    }

    @Override
    public Optional<String> findIdByUserId(UUID userId) {
        var value = userId.toString();
        List<Map<String, Object>> users = call(() -> restClient.get()
                .uri(b -> b.path("/users")
                        .queryParam("q", ATTR_USER_ID + ":" + value)
                        .queryParam("exact", true)
                        .queryParam("briefRepresentation", false)
                        .build())
                .headers(this::authorize)
                .retrieve()
                .body(LIST_OF_MAPS));
        return Optional.ofNullable(users).orElse(List.of()).stream()
                .filter(u -> value.equalsIgnoreCase(firstAttribute(u, ATTR_USER_ID)))
                .map(u -> (String) u.get("id"))
                .findFirst();
    }

    @Override
    public Optional<String> findIdByEmail(String email) {
        List<Map<String, Object>> users = call(() -> restClient.get()
                .uri(b -> b.path("/users")
                        .queryParam("email", email)
                        .queryParam("exact", true)
                        .build())
                .headers(this::authorize)
                .retrieve()
                .body(LIST_OF_MAPS));
        return Optional.ofNullable(users).orElse(List.of()).stream()
                .filter(u -> email.equalsIgnoreCase((String) u.get("email")))
                .map(u -> (String) u.get("id"))
                .findFirst();
    }

    @Override
    public void deleteUser(String idpUserId) {
        call(() -> restClient.delete().uri("/users/{id}", idpUserId)
                .headers(this::authorize)
                .retrieve()
                .toBodilessEntity());
        log.info("Identity provider user removed: idpUserId={}", idpUserId);
    }

    @Override
    public void updateUser(IdentityUser user) {
        findOrWarn(user.userId(), "update").ifPresent(id -> {
            var representation = getUser(id);
            applyProfile(representation, user);
            putUser(id, representation);
            syncRole(id, user.role());
        });
    }

    @Override
    public void setEnabled(UUID userId, boolean enabled) {
        findOrWarn(userId, enabled ? "enable" : "disable").ifPresent(id -> {
            var representation = getUser(id);
            representation.put("enabled", enabled);
            putUser(id, representation);
            if (!enabled) {
                // Sem sessão, o próximo refresh já falha; o access token em uso vale até expirar (CA4).
                call(() -> restClient.post().uri("/users/{id}/logout", id)
                        .headers(this::authorize)
                        .retrieve()
                        .toBodilessEntity());
            }
            log.info("Identity provider user {}: userId={}", enabled ? "enabled" : "disabled", userId);
        });
    }

    @Override
    public void markEmailVerified(UUID userId) {
        findOrWarn(userId, "mark email verified").ifPresent(id -> {
            var representation = getUser(id);
            representation.put("emailVerified", true);
            putUser(id, representation);
        });
    }

    @Override
    public void sendSetPasswordEmail(String idpUserId) {
        call(() -> restClient.put()
                .uri(b -> b.path("/users/{id}/execute-actions-email")
                        .queryParam("client_id", webClientId)
                        .queryParam("redirect_uri", actionRedirectUri)
                        .build(idpUserId))
                .headers(this::authorize)
                .body(List.of(UPDATE_PASSWORD))
                .retrieve()
                .toBodilessEntity());
        log.info("Identity provider set-password email requested: idpUserId={}", idpUserId);
    }

    private Optional<String> findOrWarn(UUID userId, String action) {
        var id = findIdByUserId(userId);
        if (id.isEmpty()) {
            log.warn("Identity provider user not found for {}: userId={}", action, userId);
        }
        return id;
    }

    private Map<String, Object> getUser(String id) {
        Map<String, Object> representation = call(() -> restClient.get().uri("/users/{id}", id)
                .headers(this::authorize)
                .retrieve()
                .body(MAP));
        return new HashMap<>(Objects.requireNonNull(representation));
    }

    private void putUser(String id, Map<String, Object> representation) {
        call(() -> restClient.put().uri("/users/{id}", id)
                .headers(this::authorize)
                .body(representation)
                .retrieve()
                .toBodilessEntity());
    }

    /** Deixa o usuário só com o papel de aplicação {@code role} (outros realm roles ficam). */
    private void syncRole(String id, Role role) {
        List<Map<String, Object>> current = call(() -> restClient.get()
                .uri("/users/{id}/role-mappings/realm", id)
                .headers(this::authorize)
                .retrieve()
                .body(LIST_OF_MAPS));
        var mapped = Optional.ofNullable(current).orElse(List.of());
        var stale = mapped.stream()
                .filter(r -> APP_ROLES.contains((String) r.get("name")) && !role.name().equals(r.get("name")))
                .toList();
        if (!stale.isEmpty()) {
            call(() -> restClient.method(HttpMethod.DELETE).uri("/users/{id}/role-mappings/realm", id)
                    .headers(this::authorize)
                    .body(stale)
                    .retrieve()
                    .toBodilessEntity());
        }
        if (mapped.stream().anyMatch(r -> role.name().equals(r.get("name")))) {
            return;
        }
        // A rota de "disponíveis" só exige view-users (o service account não tem view-realm).
        List<Map<String, Object>> available = call(() -> restClient.get()
                .uri("/users/{id}/role-mappings/realm/available", id)
                .headers(this::authorize)
                .retrieve()
                .body(LIST_OF_MAPS));
        var target = Optional.ofNullable(available).orElse(List.of()).stream()
                .filter(r -> role.name().equals(r.get("name")))
                .findFirst()
                .orElseThrow(() -> new DependencyUnavailableException(DEPENDENCY,
                        new IllegalStateException("Realm role ausente no Keycloak: " + role)));
        call(() -> restClient.post().uri("/users/{id}/role-mappings/realm", id)
                .headers(this::authorize)
                .body(List.of(target))
                .retrieve()
                .toBodilessEntity());
    }

    private static void applyProfile(Map<String, Object> representation, IdentityUser user) {
        var names = splitName(user.name());
        representation.put("email", user.email());
        representation.put("firstName", names[0]);
        representation.put("lastName", names[1]);
        @SuppressWarnings("unchecked")
        var existing = representation.get("attributes") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<String, Object>();
        existing.put(ATTR_USER_ID, List.of(user.userId().toString()));
        existing.put(ATTR_CPF, List.of(user.cpf()));
        existing.put(ATTR_PHONE, List.of(user.phone()));
        existing.put(ATTR_TYPE, List.of(user.type().name()));
        representation.put("attributes", existing);
    }

    /** firstName = primeira palavra, lastName = o resto (null se nome único): o claim name junta os dois. */
    static String[] splitName(String fullName) {
        var trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, null};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private static String firstAttribute(Map<String, Object> user, String name) {
        if (user.get("attributes") instanceof Map<?, ?> attributes
                && attributes.get(name) instanceof List<?> values
                && !values.isEmpty()) {
            return String.valueOf(values.getFirst());
        }
        return null;
    }

    private static String idFromLocation(URI location) {
        if (location == null) {
            throw new DependencyUnavailableException(DEPENDENCY,
                    new IllegalStateException("Keycloak não devolveu Location ao criar usuário."));
        }
        var path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void deleteQuietly(String idpUserId) {
        try {
            deleteUser(idpUserId);
        } catch (RuntimeException e) {
            log.error("Could not remove identity provider user after failure: idpUserId={}", idpUserId, e);
        }
    }

    private void authorize(HttpHeaders headers) {
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader.get());
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (BusinessException | DependencyUnavailableException e) {
            throw e;
        } catch (HttpClientErrorException.Conflict e) {
            throw new ConflictException(EMAIL_CONFLICT_MESSAGE);
        } catch (HttpClientErrorException.BadRequest e) {
            // Sem o corpo no log: a mensagem do Keycloak pode ecoar dados pessoais.
            log.warn("Keycloak Admin API rejected the request: status={}", e.getStatusCode());
            throw new BusinessRuleException(REJECTED_MESSAGE);
        } catch (RuntimeException e) {
            log.error("Keycloak Admin API call failed: {}", e.getClass().getSimpleName());
            throw new DependencyUnavailableException(DEPENDENCY, e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
