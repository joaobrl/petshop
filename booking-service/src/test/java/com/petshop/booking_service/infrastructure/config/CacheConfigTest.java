package com.petshop.booking_service.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

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
}
