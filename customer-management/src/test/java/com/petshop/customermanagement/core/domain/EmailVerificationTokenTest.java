package com.petshop.customermanagement.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationTokenTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-02T10:00:00Z");

    private static EmailVerificationToken token(Instant usedAt) {
        return new EmailVerificationToken(UUID.randomUUID(), UUID.randomUUID(), "hash", CREATED_AT, EXPIRES_AT, usedAt);
    }

    @Nested
    class Construction {

        @Test
        @DisplayName("Deve gerar id e deixar usedAt nulo quando criado pelo construtor de negócio")
        void shouldGenerateIdAndLeaveUsedAtNullWhenCreatedByBusinessConstructor() {
            var customerId = UUID.randomUUID();

            var token = new EmailVerificationToken(customerId, "hash", CREATED_AT, EXPIRES_AT);

            assertThat(token.getId()).isNotNull();
            assertThat(token.getCustomerId()).isEqualTo(customerId);
            assertThat(token.getTokenHash()).isEqualTo("hash");
            assertThat(token.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(token.getUsedAt()).isNull();
        }
    }

    @Nested
    class IsUsed {

        @Test
        @DisplayName("Deve retornar true quando usedAt estiver preenchido")
        void shouldReturnTrueWhenUsedAtIsSet() {
            assertThat(token(CREATED_AT.plusSeconds(5)).isUsed()).isTrue();
        }

        @Test
        @DisplayName("Deve retornar false quando usedAt for nulo")
        void shouldReturnFalseWhenUsedAtIsNull() {
            assertThat(token(null).isUsed()).isFalse();
        }
    }

    @Nested
    class IsExpiredAt {

        @Test
        @DisplayName("Deve retornar false quando o instante for anterior à expiração")
        void shouldReturnFalseWhenInstantIsBeforeExpiration() {
            assertThat(token(null).isExpiredAt(EXPIRES_AT.minusNanos(1))).isFalse();
        }

        @Test
        @DisplayName("Deve retornar true quando o instante for igual à expiração")
        void shouldReturnTrueWhenInstantEqualsExpiration() {
            assertThat(token(null).isExpiredAt(EXPIRES_AT)).isTrue();
        }

        @Test
        @DisplayName("Deve retornar true quando o instante for posterior à expiração")
        void shouldReturnTrueWhenInstantIsAfterExpiration() {
            assertThat(token(null).isExpiredAt(EXPIRES_AT.plusNanos(1))).isTrue();
        }
    }
}
