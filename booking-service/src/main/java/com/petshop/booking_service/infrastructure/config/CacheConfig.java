package com.petshop.booking_service.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AVAILABLE_SLOTS_CACHE = "availableSlots";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(AVAILABLE_SLOTS_CACHE);
    }
}
