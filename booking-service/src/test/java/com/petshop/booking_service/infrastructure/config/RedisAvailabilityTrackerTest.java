package com.petshop.booking_service.infrastructure.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAvailabilityTrackerTest {

    private static final RedisConnectionFailureException ERROR = new RedisConnectionFailureException("down");
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private RedisAvailabilityTracker tracker;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RedisAvailabilityTracker.class);
        logger.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        clock = new MutableClock();
        tracker = new RedisAvailabilityTracker(clock, RETRY_INTERVAL);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("Deve registrar WARN quando a primeira falha ocorrer")
    void shouldLogWarnWhenFirstFailureOccurs() {
        tracker.onFailure("get", "GROOMER:2026-08-15", ERROR);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("RedisConnectionFailureException", "get", "GROOMER:2026-08-15");
    }

    @Test
    @DisplayName("Não deve repetir WARN quando a falha continuar")
    void shouldNotRepeatWarnWhenFailureContinues() {
        tracker.onFailure("get", "k", ERROR);
        tracker.onFailure("put", "k", ERROR);
        tracker.onFailure("evict", "k", ERROR);

        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.WARN, Level.DEBUG, Level.DEBUG);
    }

    @Test
    @DisplayName("Deve registrar INFO quando o Redis voltar depois de uma falha")
    void shouldLogInfoWhenRedisRecoversAfterFailure() {
        tracker.onFailure("get", "k", ERROR);
        appender.list.clear();

        tracker.onSuccess();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("Deve registrar WARN novamente quando uma nova falha ocorrer após a recuperação")
    void shouldLogWarnAgainWhenFailureOccursAfterRecovery() {
        tracker.onFailure("get", "k", ERROR);
        tracker.onSuccess();
        appender.list.clear();

        tracker.onFailure("get", "k", ERROR);

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("Não deve registrar nada quando houver sucesso sem falha anterior")
    void shouldNotLogWhenSuccessWithoutPreviousFailure() {
        tracker.onSuccess();
        tracker.onSuccess();

        assertThat(appender.list).isEmpty();
    }
    @Test
    @DisplayName("Não deve pular o Redis quando não houve falha")
    void shouldNotSkipRedisWhenNoFailureOccurred() {
        assertThat(tracker.shouldSkipRedis()).isFalse();
    }

    @Test
    @DisplayName("Deve pular o Redis enquanto durar a pausa depois de uma falha")
    void shouldSkipRedisWhileFailurePauseLasts() {
        tracker.onFailure("get", "k", ERROR);
        clock.advance(RETRY_INTERVAL.minusMillis(1));

        assertThat(tracker.shouldSkipRedis()).isTrue();
        assertThat(tracker.shouldSkipRedis()).isTrue();
    }

    @Test
    @DisplayName("Deve liberar uma única tentativa quando o intervalo de pausa terminar")
    void shouldAllowSingleAttemptWhenPauseIntervalEnds() {
        tracker.onFailure("get", "k", ERROR);
        clock.advance(RETRY_INTERVAL);

        assertThat(tracker.shouldSkipRedis()).isFalse();
        assertThat(tracker.shouldSkipRedis()).isTrue();
    }

    @Test
    @DisplayName("Deve reabrir a pausa quando a tentativa após o intervalo falhar")
    void shouldReopenPauseWhenAttemptAfterIntervalFails() {
        tracker.onFailure("get", "k", ERROR);
        clock.advance(RETRY_INTERVAL);
        tracker.shouldSkipRedis();
        tracker.onFailure("get", "k", ERROR);
        clock.advance(RETRY_INTERVAL.minusMillis(1));

        assertThat(tracker.shouldSkipRedis()).isTrue();
        clock.advance(Duration.ofMillis(1));
        assertThat(tracker.shouldSkipRedis()).isFalse();
    }

    @Test
    @DisplayName("Não deve pular o Redis depois que ele voltar")
    void shouldNotSkipRedisAfterRedisRecovers() {
        tracker.onFailure("get", "k", ERROR);
        clock.advance(RETRY_INTERVAL);
        tracker.shouldSkipRedis();
        tracker.onSuccess();

        assertThat(tracker.shouldSkipRedis()).isFalse();
        assertThat(tracker.shouldSkipRedis()).isFalse();
    }

    @Test
    @DisplayName("Deve indicar a volta apenas na primeira operação bem-sucedida após a falha")
    void shouldReportRecoveryOnlyOnFirstSuccessAfterFailure() {
        tracker.onFailure("get", "k", ERROR);

        assertThat(tracker.onSuccess()).isTrue();
        assertThat(tracker.onSuccess()).isFalse();
    }

    @Test
    @DisplayName("Não deve registrar log quando o Redis for pulado durante a pausa")
    void shouldNotLogWhenRedisIsSkippedDuringPause() {
        tracker.onFailure("get", "k", ERROR);
        appender.list.clear();

        tracker.shouldSkipRedis();
        tracker.shouldSkipRedis();

        assertThat(appender.list).isEmpty();
    }
}
