package com.petshop.staff_service.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.ServicePermissions;
import com.petshop.commons.security.jwt.AccountType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emite, só nos testes, access tokens no formato do Keycloak (SDD-024 RF4): RS256
 * assinado com uma chave de teste gerada em memória — sem Keycloak real. O
 * {@link TestJwtDecoderConfig} troca o JwtDecoder do JWKS por um que confere esta
 * chave, com os mesmos validadores de produção (iss, exp, aud).
 *
 * <p>Mesma assinatura de métodos do antigo {@code JwtService} HS256, para os testes
 * existentes mudarem só o tipo. {@code aud} traz todos os serviços, como o token do
 * client {@code web}.
 */
public class TestJwtService {

    public static final String ISSUER = "http://localhost:8180/realms/petshop";
    public static final List<String> AUDIENCES = List.of(
            "customer-management", "product-registration", "staff-service", "booking-service", "order-service");

    /** Permissões de cada service account, iguais às de infra/keycloak/realm-petshop.json. */
    private static final Map<String, List<String>> SERVICE_PERMISSIONS = Map.of(
            "booking-service", List.of(ServicePermissions.STAFF_AVAILABILITY_READ),
            "order-service", List.of(ServicePermissions.CUSTOMERS_CUSTOMER_READ, ServicePermissions.PRODUCTS_STOCK_WRITE),
            "staff-service", List.of(ServicePermissions.CUSTOMERS_STAFF_READ));

    private static final KeyPair KEYS = generateKeys();

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    public String generateToken(UUID userId, String email, String name, String cpf, String phone, Role role, AccountType type) {
        return sign(baseClaims("web")
                .subject(UUID.randomUUID().toString())
                .claim("user_id", userId.toString())
                .claim("email", email)
                .claim("name", name)
                .claim("cpf", cpf)
                .claim("phone", phone)
                .claim("type", type.name())
                .claim("realm_access", Map.of("roles", List.of(role.name())))
                .build());
    }

    /** Token de service account (Client Credentials) do client dado, com as permissões dele no realm. */
    public String generateServiceToken(String clientId) {
        return sign(baseClaims(clientId)
                .subject(UUID.nameUUIDFromBytes(clientId.getBytes()).toString())
                .claim("realm_access", Map.of("roles", SERVICE_PERMISSIONS.getOrDefault(clientId, List.of())))
                .build());
    }

    /** Token de usuário cujo {@code aud} traz só o serviço dado (para testar recusa por audience). */
    public String generateTokenWithAudience(String audience, UUID userId, Role role, AccountType type) {
        return sign(baseClaims("web")
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .claim("user_id", userId.toString())
                .claim("type", type.name())
                .claim("realm_access", Map.of("roles", List.of(role.name())))
                .build());
    }

    /** Token de service account cujo {@code aud} traz só o serviço dado, com as permissões do client no realm. */
    public String generateServiceTokenWithAudience(String clientId, String audience) {
        return sign(baseClaims(clientId)
                .audience(audience)
                .subject(UUID.nameUUIDFromBytes(clientId.getBytes()).toString())
                .claim("realm_access", Map.of("roles", SERVICE_PERMISSIONS.getOrDefault(clientId, List.of())))
                .build());
    }

    private static JWTClaimsSet.Builder baseClaims(String azp) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCES)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("azp", azp);
    }

    private static String sign(JWTClaimsSet claims) {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
