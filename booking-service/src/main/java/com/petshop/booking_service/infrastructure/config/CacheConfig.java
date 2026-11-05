package com.petshop.booking_service.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Por padrão o cache é o Redis compartilhado ({@link RedisCacheConfig}). Com
 * {@code booking.cache.type=simple} (perfil de teste) volta o cache em memória,
 * para os testes não exigirem um Redis rodando.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AVAILABLE_SLOTS_CACHE = "availableSlots";

    @Bean
    @ConditionalOnProperty(name = "booking.cache.type", havingValue = "simple")
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(AVAILABLE_SLOTS_CACHE);
    }
}
