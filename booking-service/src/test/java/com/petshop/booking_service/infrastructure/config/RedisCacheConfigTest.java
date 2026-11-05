package com.petshop.booking_service.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Clock;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class RedisCacheConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    private final CacheErrorHandler errorHandler = new RedisCacheConfig(Clock.systemUTC(), Duration.ofSeconds(30)).errorHandler();

    static Stream<Arguments> operations() {
        return Stream.of(
                operation("Get", (handler, error) -> handler.handleCacheGetError(error, null, "k")),
                operation("Put", (handler, error) -> handler.handleCachePutError(error, null, "k", "v")),
                operation("Evict", (handler, error) -> handler.handleCacheEvictError(error, null, "k")),
                operation("Clear", (handler, error) -> handler.handleCacheClearError(error, null)));
    }

    private static Arguments operation(String name, BiConsumer<CacheErrorHandler, RuntimeException> call) {
        return Arguments.of(name, call);
    }

    @Test
    @DisplayName("Deve montar o gerenciador fail-open apenas com o cache de horários disponíveis")
    void shouldBuildFailOpenManagerWithOnlyAvailableSlotsCache() {
        var manager = new RedisCacheConfig(Clock.systemUTC(), Duration.ofSeconds(30)).cacheManager(connectionFactory, Duration.ofMinutes(5));

        assertThat(manager).isInstanceOf(FailOpenCacheManager.class);
        assertThat(manager.getCacheNames()).containsExactly(CacheConfig.AVAILABLE_SLOTS_CACHE);
        assertThat(manager.getCache(CacheConfig.AVAILABLE_SLOTS_CACHE)).isNotNull();
        assertThat(manager.getCache("unknown")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("Deve engolir a falha de cache no error handler")
    void shouldSwallowCacheFailureWhenErrorHandlerReceivesRedisError(
            String name, BiConsumer<CacheErrorHandler, RuntimeException> call) {
        assertThatCode(() -> call.accept(errorHandler, new RedisConnectionFailureException("down")))
                .doesNotThrowAnyException();
        assertThatCode(() -> call.accept(errorHandler, new SerializationException("bad json")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("Deve relançar o erro no error handler quando não for falha de cache")
    void shouldRethrowWhenErrorHandlerReceivesNonCacheFailure(
            String name, BiConsumer<CacheErrorHandler, RuntimeException> call) {
        assertThatThrownBy(() -> call.accept(errorHandler, new IllegalStateException("bug")))
                .isInstanceOf(IllegalStateException.class);
    }
}
