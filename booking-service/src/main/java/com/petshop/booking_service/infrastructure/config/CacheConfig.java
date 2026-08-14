package com.petshop.booking_service.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
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
}
