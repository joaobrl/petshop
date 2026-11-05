package com.petshop.booking_service.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailOpenCacheManagerTest {

    private static final String CACHE_NAME = "availableSlots";
    private static final String KEY = "GROOMER:2026-08-15";
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    @Mock
    private CacheManager delegateManager;

    @Mock
    private Cache delegateCache;

    @Mock
    private RedisAvailabilityTracker tracker;

    private FailOpenCacheManager.FailOpenCache cache;

    @BeforeEach
    void setUp() {
        cache = new FailOpenCacheManager.FailOpenCache(delegateCache, tracker);
    }

    static Stream<Arguments> cacheFailures() {
        return Stream.of(
                Arguments.of(new RedisConnectionFailureException("down"), true),
                Arguments.of(new SerializationException("bad json"), true),
                Arguments.of(new IllegalStateException("bug"), false));
    }

    @ParameterizedTest
    @MethodSource("cacheFailures")
    @DisplayName("Deve classificar o erro como falha de cache apenas para erros de Redis e serialização")
    void shouldClassifyCacheFailureWhenErrorTypeVaries(Throwable error, boolean expected) {
        assertThat(FailOpenCacheManager.isCacheFailure(error)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Deve devolver cache fail-open quando o delegate possuir o cache")
    void shouldReturnFailOpenCacheWhenDelegateHasCache() {
        when(delegateManager.getCache(CACHE_NAME)).thenReturn(delegateCache);
        var manager = new FailOpenCacheManager(delegateManager, tracker);

        var result = manager.getCache(CACHE_NAME);

        assertThat(result).isInstanceOf(FailOpenCacheManager.FailOpenCache.class);
    }

    @Test
    @DisplayName("Deve devolver nulo quando o delegate não possuir o cache")
    void shouldReturnNullWhenDelegateHasNoCache() {
        when(delegateManager.getCache("unknown")).thenReturn(null);
        var manager = new FailOpenCacheManager(delegateManager, tracker);

        assertThat(manager.getCache("unknown")).isNull();
    }

    @Test
    @DisplayName("Deve devolver a mesma instância quando o cache for pedido duas vezes")
    void shouldReturnSameInstanceWhenCacheRequestedTwice() {
        when(delegateManager.getCache(CACHE_NAME)).thenReturn(delegateCache);
        var manager = new FailOpenCacheManager(delegateManager, tracker);

        var first = manager.getCache(CACHE_NAME);
        var second = manager.getCache(CACHE_NAME);

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("Deve devolver os nomes do delegate ao listar os caches")
    void shouldReturnDelegateNamesWhenListingCacheNames() {
        when(delegateManager.getCacheNames()).thenReturn(List.of(CACHE_NAME));
        var manager = new FailOpenCacheManager(delegateManager, tracker);

        assertThat(manager.getCacheNames()).containsExactly(CACHE_NAME);
    }

    @Test
    @DisplayName("Deve delegar nome e cache nativo")
    void shouldDelegateNameAndNativeCache() {
        var nativeCache = new Object();
        when(delegateCache.getName()).thenReturn(CACHE_NAME);
        when(delegateCache.getNativeCache()).thenReturn(nativeCache);

        assertThat(cache.getName()).isEqualTo(CACHE_NAME);
        assertThat(cache.getNativeCache()).isSameAs(nativeCache);
    }

    @Test
    @DisplayName("Deve devolver o wrapper quando o Redis responder no get por chave")
    void shouldReturnWrapperWhenRedisRespondsOnGetByKey() {
        Cache.ValueWrapper wrapper = () -> "v";
        when(delegateCache.get(KEY)).thenReturn(wrapper);

        var result = cache.get(KEY);

        assertThat(result).isSameAs(wrapper);
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve devolver nulo quando o Redis falhar no get por chave")
    void shouldReturnNullWhenRedisFailsOnGetByKey() {
        var error = new RedisConnectionFailureException("down");
        when(delegateCache.get(KEY)).thenThrow(error);

        var result = cache.get(KEY);

        assertThat(result).isNull();
        verify(tracker).onFailure("get", KEY, error);
    }

    @Test
    @DisplayName("Deve relançar o erro quando o get por chave falhar por motivo que não é do Redis")
    void shouldRethrowWhenGetByKeyFailsWithNonCacheError() {
        when(delegateCache.get(KEY)).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> cache.get(KEY)).isInstanceOf(IllegalStateException.class);

        verify(tracker, never()).onFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Deve devolver o valor quando o Redis responder no get por chave e tipo")
    void shouldReturnValueWhenRedisRespondsOnGetByKeyAndType() {
        when(delegateCache.get(KEY, String.class)).thenReturn("v");

        var result = cache.get(KEY, String.class);

        assertThat(result).isEqualTo("v");
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve devolver nulo quando o Redis falhar no get por chave e tipo")
    void shouldReturnNullWhenRedisFailsOnGetByKeyAndType() {
        when(delegateCache.get(KEY, String.class)).thenThrow(new SerializationException("bad json"));

        var result = cache.get(KEY, String.class);

        assertThat(result).isNull();
        verify(tracker).onFailure(eq("get"), eq(KEY), any(SerializationException.class));
    }

    @Test
    @DisplayName("Deve relançar o erro quando o get por chave e tipo falhar por motivo que não é do Redis")
    void shouldRethrowWhenGetByKeyAndTypeFailsWithNonCacheError() {
        when(delegateCache.get(KEY, String.class)).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> cache.get(KEY, String.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Deve devolver o valor do cache quando o Redis responder no get com loader")
    void shouldReturnCachedValueWhenRedisResponds() {
        Callable<String> loader = () -> "fresh";
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any())).thenReturn("cached");

        var result = cache.get(KEY, loader);

        assertThat(result).isEqualTo("cached");
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve propagar ValueRetrievalException quando o loader falhar")
    void shouldPropagateValueRetrievalExceptionWhenLoaderFails() {
        Callable<String> loader = () -> "fresh";
        var failure = new Cache.ValueRetrievalException(KEY, loader, new IllegalArgumentException("loader"));
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any())).thenThrow(failure);

        assertThatThrownBy(() -> cache.get(KEY, loader)).isSameAs(failure);

        verify(tracker, never()).onFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Deve relançar o erro quando ele não for falha de cache no get com loader")
    void shouldRethrowWhenErrorIsNotCacheFailure() {
        Callable<String> loader = () -> "fresh";
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any()))
                .thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> cache.get(KEY, loader)).isInstanceOf(IllegalStateException.class);

        verify(tracker, never()).onFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Deve chamar o loader direto quando o Redis falhar antes de carregar")
    void shouldCallLoaderDirectlyWhenRedisFailsBeforeLoading() {
        var calls = new AtomicInteger();
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            return "origin";
        };
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        var result = cache.get(KEY, loader);

        assertThat(result).isEqualTo("origin");
        assertThat(calls).hasValue(1);
        verify(tracker).onFailure(eq("get"), eq(KEY), any(RedisConnectionFailureException.class));
    }

    @Test
    @DisplayName("Deve reaproveitar o valor calculado quando o Redis falhar depois do loader")
    void shouldReuseComputedValueWhenRedisFailsAfterLoading() {
        var calls = new AtomicInteger();
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            return "origin";
        };
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any())).thenAnswer(invocation -> {
            invocation.<Callable<String>>getArgument(1).call();
            throw new RedisConnectionFailureException("down while writing");
        });

        var result = cache.get(KEY, loader);

        assertThat(result).isEqualTo("origin");
        assertThat(calls).hasValue(1);
        verify(tracker).onFailure(eq("get"), eq(KEY), any(RedisConnectionFailureException.class));
    }

    @Test
    @DisplayName("Deve embrulhar em ValueRetrievalException quando o loader de fallback falhar")
    void shouldWrapInValueRetrievalExceptionWhenFallbackLoaderFails() {
        var cause = new IllegalArgumentException("origin down");
        Callable<String> loader = () -> {
            throw cause;
        };
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> cache.get(KEY, loader))
                .isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("Deve delegar o put quando o Redis responder")
    void shouldDelegatePutWhenRedisResponds() {
        cache.put(KEY, "v");

        verify(delegateCache).put(KEY, "v");
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve engolir a falha do Redis no put")
    void shouldSwallowRedisFailureWhenPutFails() {
        var error = new RedisConnectionFailureException("down");
        doThrow(error).when(delegateCache).put(KEY, "v");

        assertThatCode(() -> cache.put(KEY, "v")).doesNotThrowAnyException();

        verify(tracker).onFailure("put", KEY, error);
    }

    @Test
    @DisplayName("Deve delegar o putIfAbsent quando o Redis responder")
    void shouldDelegatePutIfAbsentWhenRedisResponds() {
        Cache.ValueWrapper existing = () -> "old";
        when(delegateCache.putIfAbsent(KEY, "v")).thenReturn(existing);

        var result = cache.putIfAbsent(KEY, "v");

        assertThat(result).isSameAs(existing);
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve devolver nulo quando o Redis falhar no putIfAbsent")
    void shouldReturnNullWhenPutIfAbsentFails() {
        var error = new RedisConnectionFailureException("down");
        when(delegateCache.putIfAbsent(KEY, "v")).thenThrow(error);

        var result = cache.putIfAbsent(KEY, "v");

        assertThat(result).isNull();
        verify(tracker).onFailure("putIfAbsent", KEY, error);
    }

    @Test
    @DisplayName("Deve delegar o evict quando o Redis responder")
    void shouldDelegateEvictWhenRedisResponds() {
        cache.evict(KEY);

        verify(delegateCache).evict(KEY);
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve engolir a falha do Redis no evict")
    void shouldSwallowRedisFailureWhenEvictFails() {
        var error = new RedisConnectionFailureException("down");
        doThrow(error).when(delegateCache).evict(KEY);

        assertThatCode(() -> cache.evict(KEY)).doesNotThrowAnyException();

        verify(tracker).onFailure("evict", KEY, error);
    }

    @Test
    @DisplayName("Deve relançar o erro quando o evict falhar por motivo que não é do Redis")
    void shouldRethrowWhenEvictFailsWithNonCacheError() {
        doThrow(new IllegalStateException("bug")).when(delegateCache).evict(KEY);

        assertThatThrownBy(() -> cache.evict(KEY)).isInstanceOf(IllegalStateException.class);

        verify(tracker, never()).onFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Deve delegar o clear quando o Redis responder")
    void shouldDelegateClearWhenRedisResponds() {
        cache.clear();

        verify(delegateCache).clear();
        verify(tracker).onSuccess();
    }

    @Test
    @DisplayName("Deve engolir a falha do Redis no clear")
    void shouldSwallowRedisFailureWhenClearFails() {
        var error = new RedisConnectionFailureException("down");
        doThrow(error).when(delegateCache).clear();

        assertThatCode(() -> cache.clear()).doesNotThrowAnyException();

        verify(tracker).onFailure("clear", "*", error);
    }
    private FailOpenCacheManager.FailOpenCache pausedCache(MutableClock clock) {
        var realTracker = new RedisAvailabilityTracker(clock, RETRY_INTERVAL);
        realTracker.onFailure("get", KEY, new RedisConnectionFailureException("down"));
        return new FailOpenCacheManager.FailOpenCache(delegateCache, realTracker);
    }

    @Test
    @DisplayName("Não deve chamar o Redis no get por chave durante a pausa")
    void shouldNotCallRedisOnGetByKeyDuringPause() {
        var pausedCache = pausedCache(new MutableClock());

        assertThat(pausedCache.get(KEY)).isNull();
        verifyNoInteractions(delegateCache);
    }

    @Test
    @DisplayName("Não deve chamar o Redis no get por chave e tipo durante a pausa")
    void shouldNotCallRedisOnGetByKeyAndTypeDuringPause() {
        var pausedCache = pausedCache(new MutableClock());

        assertThat(pausedCache.get(KEY, String.class)).isNull();
        verifyNoInteractions(delegateCache);
    }

    @Test
    @DisplayName("Deve chamar o loader direto sem chamar o Redis no get com loader durante a pausa")
    void shouldCallLoaderDirectlyWithoutRedisOnGetWithLoaderDuringPause() {
        var pausedCache = pausedCache(new MutableClock());
        var calls = new AtomicInteger();
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            return "origin";
        };

        assertThat(pausedCache.get(KEY, loader)).isEqualTo("origin");
        assertThat(pausedCache.get(KEY, loader)).isEqualTo("origin");

        assertThat(calls).hasValue(2);
        verifyNoInteractions(delegateCache);
    }

    @Test
    @DisplayName("Deve embrulhar em ValueRetrievalException quando o loader falhar durante a pausa")
    void shouldWrapInValueRetrievalExceptionWhenLoaderFailsDuringPause() {
        var pausedCache = pausedCache(new MutableClock());
        var cause = new IllegalArgumentException("origin down");
        Callable<String> loader = () -> {
            throw cause;
        };

        assertThatThrownBy(() -> pausedCache.get(KEY, loader))
                .isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCause(cause);
        verifyNoInteractions(delegateCache);
    }

    @Test
    @DisplayName("Não deve chamar o Redis em put, putIfAbsent, evict e clear durante a pausa")
    void shouldNotCallRedisOnWritesDuringPause() {
        var pausedCache = pausedCache(new MutableClock());

        pausedCache.put(KEY, "v");
        var existing = pausedCache.putIfAbsent(KEY, "v");
        pausedCache.evict(KEY);
        pausedCache.clear();

        assertThat(existing).isNull();
        verifyNoInteractions(delegateCache);
    }

    @Test
    @DisplayName("Deve tentar o Redis de novo quando o intervalo de pausa terminar")
    void shouldRetryRedisWhenPauseIntervalEnds() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        when(delegateCache.get(KEY)).thenReturn(null);
        clock.advance(RETRY_INTERVAL);

        pausedCache.get(KEY);

        verify(delegateCache).get(KEY);
    }

    @Test
    @DisplayName("Deve reabrir a pausa quando a nova tentativa do Redis falhar")
    void shouldReopenPauseWhenRetryFails() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        when(delegateCache.get(KEY)).thenThrow(new RedisConnectionFailureException("still down"));
        clock.advance(RETRY_INTERVAL);

        pausedCache.get(KEY);
        pausedCache.get(KEY);
        pausedCache.get(KEY);

        verify(delegateCache, times(1)).get(KEY);
    }

    @Test
    @DisplayName("Deve limpar o cache delegado uma única vez quando o Redis voltar")
    void shouldClearDelegateOnceWhenRedisRecovers() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        clock.advance(RETRY_INTERVAL);

        pausedCache.get(KEY);
        pausedCache.get(KEY);
        pausedCache.evict(KEY);

        verify(delegateCache, times(1)).clear();
    }

    @Test
    @DisplayName("Não deve limpar o cache delegado quando o Redis nunca ficou indisponível")
    void shouldNotClearDelegateWhenRedisWasNeverUnavailable() {
        var healthyCache = new FailOpenCacheManager.FailOpenCache(
                delegateCache, new RedisAvailabilityTracker(new MutableClock(), RETRY_INTERVAL));

        healthyCache.get(KEY);
        healthyCache.put(KEY, "v");

        verify(delegateCache, never()).clear();
    }

    @Test
    @DisplayName("Deve limpar o cache delegado quando o Redis voltar numa operação com loader")
    void shouldClearDelegateWhenRedisRecoversOnLoaderOperation() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        when(delegateCache.get(eq(KEY), ArgumentMatchers.<Callable<String>>any())).thenReturn("cached");
        clock.advance(RETRY_INTERVAL);

        var result = pausedCache.get(KEY, () -> "fresh");

        assertThat(result).isEqualTo("cached");
        verify(delegateCache).clear();
    }

    @Test
    @DisplayName("Deve continuar em fail-open quando o clear da volta falhar")
    void shouldStayFailOpenWhenRecoveryClearFails() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        Cache.ValueWrapper wrapper = () -> "v";
        when(delegateCache.get(KEY)).thenReturn(wrapper);
        doThrow(new RedisConnectionFailureException("down again")).when(delegateCache).clear();
        clock.advance(RETRY_INTERVAL);

        var result = pausedCache.get(KEY);
        pausedCache.get(KEY);

        assertThat(result).isSameAs(wrapper);
        verify(delegateCache, times(1)).get(KEY);
        verify(delegateCache, times(1)).clear();
    }

    @Test
    @DisplayName("Deve relançar o erro quando o clear da volta falhar por motivo que não é do Redis")
    void shouldRethrowWhenRecoveryClearFailsWithNonCacheError() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        doThrow(new IllegalStateException("bug")).when(delegateCache).clear();
        clock.advance(RETRY_INTERVAL);

        assertThatThrownBy(() -> pausedCache.get(KEY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Deve limpar o cache delegado uma vez quando a volta ocorrer em operações de escrita")
    void shouldClearDelegateOnceWhenRecoveryHappensOnWriteOperations() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        clock.advance(RETRY_INTERVAL);

        pausedCache.put(KEY, "v");
        pausedCache.putIfAbsent(KEY, "v");
        pausedCache.get(KEY, String.class);

        verify(delegateCache, times(1)).clear();
    }

    @Test
    @DisplayName("Deve limpar o cache delegado uma vez quando a volta ocorrer no putIfAbsent")
    void shouldClearDelegateWhenRecoveryHappensOnPutIfAbsent() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        clock.advance(RETRY_INTERVAL);

        pausedCache.putIfAbsent(KEY, "v");

        verify(delegateCache).clear();
    }

    @Test
    @DisplayName("Deve limpar o cache delegado uma vez quando a volta ocorrer no clear explícito")
    void shouldNotClearTwiceWhenRecoveryHappensOnExplicitClear() {
        var clock = new MutableClock();
        var pausedCache = pausedCache(clock);
        clock.advance(RETRY_INTERVAL);

        pausedCache.clear();

        verify(delegateCache, times(1)).clear();
    }
}
