package com.petshop.booking_service.infrastructure.config;

import com.petshop.booking_service.core.domain.AvailableSlot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cache de horários compartilhado entre réplicas, no Redis (SDD-023). Ativo por
 * padrão; os testes usam {@code booking.cache.type=simple} (ver {@link CacheConfig}).
 */
@Configuration
@ConditionalOnProperty(name = "booking.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig implements CachingConfigurer {

    static final String KEY_PREFIX = "booking:";

    private final RedisAvailabilityTracker tracker;

    public RedisCacheConfig(
            Clock clock,
            @Value("${booking.cache.redis.retry-interval:30s}") Duration retryInterval) {
        this.tracker = new RedisAvailabilityTracker(clock, retryInterval);
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${booking.cache.available-slots.ttl:5m}") Duration ttl) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var slotsType = mapper.getTypeFactory().constructCollectionType(List.class, AvailableSlot.class);
        var valueSerializer = new JacksonJsonRedisSerializer<List<AvailableSlot>>(mapper, slotsType);

        var config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(name -> KEY_PREFIX + name + ":")
                .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        var redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(Map.of(CacheConfig.AVAILABLE_SLOTS_CACHE, config))
                .disableCreateOnMissingCache()
                .build();
        redisCacheManager.afterPropertiesSet();

        return new FailOpenCacheManager(redisCacheManager, tracker);
    }

    /** Para eventuais {@code @Cacheable} futuros: erro de cache vira log, nunca falha da chamada. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                handle("get", key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                handle("put", key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                handle("evict", key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                handle("clear", "*", e);
            }

            private void handle(String operation, Object key, RuntimeException e) {
                if (!FailOpenCacheManager.isCacheFailure(e)) {
                    throw e;
                }
                tracker.onFailure(operation, key, e);
            }
        };
    }
}
