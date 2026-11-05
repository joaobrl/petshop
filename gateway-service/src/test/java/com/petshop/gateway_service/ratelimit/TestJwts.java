package com.petshop.gateway_service.ratelimit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

/** Tokens RS256 assinados com chave de teste, no lugar do JWKS do Keycloak (SDD-024 RF4). */
final class TestJwts {

    static final String ISSUER = "http://localhost:8180/realms/petshop";
    static final KeyPair KEYS = generate();
    static final KeyPair OTHER_KEYS = generate();

    private TestJwts() {
    }

    static JwtSubjectResolver resolver() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return new JwtSubjectResolver(decoder);
    }

    static String token(KeyPair keys, String subject, String issuer, Instant expiration) {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).expirationTime(Date.from(expiration));
        if (subject != null) {
            claims.subject(subject);
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        try {
            jwt.sign(new RSASSASigner(keys.getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    static String validToken(String subject) {
        return token(KEYS, subject, ISSUER, Instant.now().plusSeconds(3600));
    }

    private static KeyPair generate() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
