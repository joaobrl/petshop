package com.petshop.booking_service.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, RedisCacheConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()));

    @Test
    void canBeInstantiated() {
        assertThat(new CacheConfig()).isNotNull();
    }

    @Test
    void createsCacheManagerWithAvailableSlotsCache() {
        var config = new CacheConfig();

        var cacheManager = config.cacheManager();

        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
        assertThat(cacheManager.getCacheNames()).containsExactly(CacheConfig.AVAILABLE_SLOTS_CACHE);
        assertThat(cacheManager.getCache(CacheConfig.AVAILABLE_SLOTS_CACHE)).isNotNull();
    }

    @Test
    @DisplayName("Deve usar o cache em memória quando o tipo for simple")
    void shouldUseInMemoryCacheWhenTypeIsSimple() {
        contextRunner.withPropertyValues("booking.cache.type=simple").run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            assertThat(context).doesNotHaveBean(RedisCacheConfig.class);
        });
    }

    @Test
    @DisplayName("Deve usar o cache Redis quando o tipo não estiver definido")
    void shouldUseRedisCacheWhenTypeIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(FailOpenCacheManager.class);
            assertThat(context).hasSingleBean(RedisCacheConfig.class);
        });
    }
}
