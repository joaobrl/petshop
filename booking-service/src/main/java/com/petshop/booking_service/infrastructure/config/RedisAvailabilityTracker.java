package com.petshop.booking_service.infrastructure.config;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registra a transição de disponibilidade do Redis do cache e controla a pausa
 * (circuit breaker simples): depois de uma falha, o cache não chama o Redis até
 * passar o intervalo de nova tentativa. {@code WARN} só ao passar de disponível
 * para indisponível, {@code INFO} na volta e {@code DEBUG} no resto.
 */
@Slf4j
class RedisAvailabilityTracker {

    private final Clock clock;
    private final long retryIntervalMillis;
    private final AtomicBoolean unavailable = new AtomicBoolean(false);
    private final AtomicLong pausedUntilMillis = new AtomicLong(0);

    RedisAvailabilityTracker(Clock clock, Duration retryInterval) {
        this.clock = clock;
        this.retryIntervalMillis = retryInterval.toMillis();
    }

    /**
     * Indica se a chamada ao Redis deve ser pulada. Passado o intervalo, devolve
     * {@code false} para uma única chamada (a tentativa); as demais seguem
     * puladas até essa tentativa terminar.
     */
    boolean shouldSkipRedis() {
        if (!unavailable.get()) {
            return false;
        }
        long until = pausedUntilMillis.get();
        long now = clock.millis();
        if (now < until) {
            return true;
        }
        return !pausedUntilMillis.compareAndSet(until, now + retryIntervalMillis);
    }

    void onFailure(String operation, Object key, Throwable cause) {
        pausedUntilMillis.set(clock.millis() + retryIntervalMillis);
        if (unavailable.compareAndSet(false, true)) {
            log.warn("Cache de horários em fail-open: Redis indisponível ({}), consultando direto da origem. "
                    + "Operação={} chave={}", cause.getClass().getSimpleName(), operation, key);
        } else {
            log.debug("Cache de horários em fail-open: falha do Redis na operação {} chave={}", operation, key, cause);
        }
    }

    /** @return {@code true} quando o Redis acabou de voltar de uma indisponibilidade. */
    boolean onSuccess() {
        if (unavailable.compareAndSet(true, false)) {
            log.info("Cache de horários: Redis respondeu de novo, cache compartilhado voltou a ser usado");
            return true;
        }
        return false;
    }
}
