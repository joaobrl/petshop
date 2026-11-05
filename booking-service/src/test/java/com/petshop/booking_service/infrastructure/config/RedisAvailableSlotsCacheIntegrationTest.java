package com.petshop.booking_service.infrastructure.config;

import com.petshop.booking_service.core.domain.AvailableSlot;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
class RedisAvailableSlotsCacheIntegrationTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY = "GROOMER:2026-08-15";
    private static final String RAW_KEY = "booking:availableSlots:GROOMER:2026-08-15";
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory factoryA;
    private LettuceConnectionFactory factoryB;
    private CacheManager replicaA;
    private CacheManager replicaB;
    private StringRedisTemplate template;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void setUp() {
        factoryA = newFactory(redis.getHost(), redis.getMappedPort(6379), Duration.ofSeconds(5));
        factoryB = newFactory(redis.getHost(), redis.getMappedPort(6379), Duration.ofSeconds(5));
        replicaA = new RedisCacheConfig(clock, RETRY_INTERVAL).cacheManager(factoryA, TTL);
        replicaB = new RedisCacheConfig(clock, RETRY_INTERVAL).cacheManager(factoryB, TTL);
        template = new StringRedisTemplate(factoryA);
        template.afterPropertiesSet();
        try (var connection = factoryA.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        factoryA.destroy();
        factoryB.destroy();
    }

    private static LettuceConnectionFactory newFactory(String host, int port, Duration timeout) {
        var clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                        .build())
                .build();
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    private static List<AvailableSlot> slots() {
        return List.copyOf(List.of(
                new AvailableSlot(LocalDateTime.of(2026, 8, 15, 9, 0), 2),
                new AvailableSlot(LocalDateTime.of(2026, 8, 15, 10, 0), 1)));
    }

    private static Cache slotsCache(CacheManager manager) {
        return manager.getCache(CacheConfig.AVAILABLE_SLOTS_CACHE);
    }

    @Test
    @DisplayName("Não deve servir a entrada removida quando outra réplica fizer evict")
    void shouldNotServeEvictedEntryWhenOtherReplicaEvicts() {
        slotsCache(replicaA).put(KEY, slots());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(slotsCache(replicaB).get(KEY)).isNotNull());

        slotsCache(replicaB).evict(KEY);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(slotsCache(replicaA).get(KEY)).isNull());
    }

    @Test
    @DisplayName("Deve gravar a entrada com TTL quando escrita")
    void shouldStoreEntryWithTtlWhenWritten() {
        slotsCache(replicaA).put(KEY, slots());

        await().atMost(Duration.ofSeconds(5)).until(() -> template.hasKey(RAW_KEY));

        var remainingMillis = template.getExpire(RAW_KEY, TimeUnit.MILLISECONDS);

        assertThat(remainingMillis).isPositive().isLessThanOrEqualTo(TTL.toMillis());
    }

    @Test
    @DisplayName("Deve ler de volta a lista de horários quando gravada como JSON")
    void shouldReadBackSlotListWhenStoredAsJson() {
        var expected = slots();
        slotsCache(replicaA).put(KEY, expected);

        await().atMost(Duration.ofSeconds(5)).until(() -> template.hasKey(RAW_KEY));

        var read = slotsCache(replicaB).get(KEY, List.class);
        var raw = template.opsForValue().get(RAW_KEY);

        assertThat(read).isEqualTo(expected);
        assertThat(raw).contains("2026-08-15T09:00:00").doesNotContain("@class");
    }

    @Test
    @DisplayName("Deve usar a chave com prefixo quando escrita")
    void shouldUsePrefixedKeyWhenWritten() {
        slotsCache(replicaA).put(KEY, slots());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(template.keys("*")).containsExactly(RAW_KEY));
    }

    @Test
    @DisplayName("Deve cair para o loader quando o Redis estiver inacessível")
    void shouldFallBackToLoaderWhenRedisIsUnreachable() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = newFactory("localhost", closedPort, Duration.ofMillis(500));
        try {
            var manager = new RedisCacheConfig(clock, RETRY_INTERVAL).cacheManager(unreachable, TTL);
            var calls = new AtomicInteger();

            var result = slotsCache(manager).get(KEY, () -> {
                calls.incrementAndGet();
                return slots();
            });

            assertThat(result).isEqualTo(slots());
            assertThat(calls).hasValue(1);
        } finally {
            unreachable.destroy();
        }
    }

    @Test
    @DisplayName("Deve pagar o timeout apenas na primeira chamada quando o Redis estiver inacessível")
    void shouldPayTimeoutOnlyOnFirstCallWhenRedisIsUnreachable() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var unreachable = spy(newFactory("localhost", closedPort, Duration.ofMillis(500)));
        try {
            var cache = slotsCache(new RedisCacheConfig(clock, RETRY_INTERVAL).cacheManager(unreachable, TTL));
            var calls = new AtomicInteger();

            for (int i = 0; i < 10; i++) {
                cache.get(KEY, () -> {
                    calls.incrementAndGet();
                    return slots();
                });
            }

            assertThat(calls).hasValue(10);
            verify(unreachable, times(1)).getConnection();
        } finally {
            unreachable.destroy();
        }
    }
}
