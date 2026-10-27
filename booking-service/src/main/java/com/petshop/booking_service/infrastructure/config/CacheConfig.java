package com.petshop.booking_service.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @EnableCaching} isolado aqui (fora de {@code BookingServiceApplication}) porque testes de slice como
 * {@code @DataJpaTest} filtram classes {@code @Configuration} normais mas não a classe raiz do Boot — deixar
 * {@code @EnableCaching} na raiz forçava a infraestrutura de cache (exige {@code CacheManager}) a entrar em
 * todo teste de slice, causando {@code NoSuchBeanDefinitionException}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AVAILABLE_SLOTS_CACHE = "availableSlots";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(AVAILABLE_SLOTS_CACHE);
    }
}
