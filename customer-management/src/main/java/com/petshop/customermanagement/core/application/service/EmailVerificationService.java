package com.petshop.customermanagement.core.application.service;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.EmailVerificationToken;
import com.petshop.customermanagement.core.domain.exception.InvalidVerificationTokenException;
import com.petshop.customermanagement.core.port.in.EmailVerificationPortIn;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.EmailVerificationTokenPortOut;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verificação de e-mail do cliente (SDD-020): token aleatório de uso único,
 * guardado só como hash SHA-256, enviado por e-mail num link para a tela
 * /verify-email do front.
 */
@Service
@Slf4j
public class EmailVerificationService implements EmailVerificationPortIn {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    static final String INVALID_TOKEN_MESSAGE =
            "Link de verificação inválido. Solicite um novo e-mail de confirmação.";
    static final String EXPIRED_TOKEN_MESSAGE =
            "Link de verificação expirado. Solicite um novo e-mail de confirmação.";
    static final String USED_TOKEN_MESSAGE =
            "Este link de verificação já foi utilizado.";

    private final CustomerPortOut customerPortOut;
    private final EmailVerificationTokenPortOut tokenPortOut;
    private final NotificationPortOut notificationPortOut;
    private final IdentityProviderPortOut identityProviderPortOut;
    private final Duration tokenTtl;
    private final String frontendBaseUrl;

    public EmailVerificationService(CustomerPortOut customerPortOut,
                                    EmailVerificationTokenPortOut tokenPortOut,
                                    NotificationPortOut notificationPortOut,
                                    IdentityProviderPortOut identityProviderPortOut,
                                    @Value("${app.email-verification.token-ttl:24h}") Duration tokenTtl,
                                    @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.customerPortOut = customerPortOut;
        this.tokenPortOut = tokenPortOut;
        this.notificationPortOut = notificationPortOut;
        this.identityProviderPortOut = identityProviderPortOut;
        this.tokenTtl = tokenTtl;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    @Override
    public void issueVerification(Customer customer) {
        var rawToken = generateRawToken();
        var now = Instant.now();
        tokenPortOut.save(new EmailVerificationToken(customer.getId(), hash(rawToken), now, now.plus(tokenTtl)));
        notificationPortOut.sendEmailVerification(customer.getEmail(), customer.getName(), verificationLink(rawToken));
        log.info("Email verification issued for customer: id={}", customer.getId());
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        var stored = tokenPortOut.findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidVerificationTokenException(INVALID_TOKEN_MESSAGE));
        if (stored.isUsed()) {
            throw new InvalidVerificationTokenException(USED_TOKEN_MESSAGE);
        }
        var now = Instant.now();
        if (stored.isExpiredAt(now)) {
            throw new InvalidVerificationTokenException(EXPIRED_TOKEN_MESSAGE);
        }
        // Update condicional: se outra requisição consumiu o mesmo token entre
        // a leitura e aqui, esta perde (CA4).
        if (!tokenPortOut.markUsed(stored.getId(), now)) {
            throw new InvalidVerificationTokenException(USED_TOKEN_MESSAGE);
        }
        var customer = customerPortOut.findById(stored.getCustomerId())
                .orElseThrow(() -> new InvalidVerificationTokenException(INVALID_TOKEN_MESSAGE));
        if (!customer.hasVerifiedEmail()) {
            customer.markEmailVerified();
            customerPortOut.save(customer);
        }
        // O claim email_verified vem do Keycloak (SDD-024 F3): sem isto o próximo token
        // continuaria false e o checkout/agendamento seguiria bloqueado. Falha aqui desfaz
        // a transação (token volta a valer).
        identityProviderPortOut.markEmailVerified(customer.getId());
        log.info("Email verified for customer: id={}", customer.getId());
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        customerPortOut.findByEmail(email)
                .filter(customer -> !customer.hasVerifiedEmail())
                .ifPresent(customer -> {
                    tokenPortOut.deleteUnusedByCustomerId(customer.getId());
                    issueVerification(customer);
                });
        // Nada encontrado (ou já verificado): não faz nada — o controller
        // devolve a mesma resposta de qualquer forma (RF4, sem enumeração).
    }

    private String verificationLink(String rawToken) {
        return frontendBaseUrl + "/verify-email?token=" + rawToken;
    }

    private static String generateRawToken() {
        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
