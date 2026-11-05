package com.petshop.booking_service.infrastructure.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decora o {@link CacheManager} do Redis para o cache nunca derrubar a consulta
 * (RF4). O {@code AvailableSlotsService} usa {@code Cache.get(key, loader)} e
 * {@code Cache.evict(key)} direto (sem {@code @Cacheable}), caminho que o
 * {@code CacheErrorHandler} do Spring não cobre — por isso o fail-open é feito aqui.
 */
class FailOpenCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final RedisAvailabilityTracker tracker;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    FailOpenCacheManager(CacheManager delegate, RedisAvailabilityTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
    }

    @Override
    public Cache getCache(@NonNull String name) {
        var cache = caches.get(name);
        if (cache == null) {
            var target = delegate.getCache(name);
            if (target == null) {
                return null;
            }
            cache = caches.computeIfAbsent(name, n -> new FailOpenCache(target, tracker));
        }
        return cache;
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    static boolean isCacheFailure(Throwable e) {
        return e instanceof DataAccessException || e instanceof SerializationException;
    }

    static class FailOpenCache implements Cache {

        private final Cache delegate;
        private final RedisAvailabilityTracker tracker;

        FailOpenCache(Cache delegate, RedisAvailabilityTracker tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }

        @Override
        @NonNull
        public String getName() {
            return delegate.getName();
        }

        @Override
        @NonNull
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(@NonNull Object key) {
            if (tracker.shouldSkipRedis()) {
                return null;
            }
            try {
                var value = delegate.get(key);
                afterSuccess();
                return value;
            } catch (RuntimeException e) {
                return failOpen("get", key, e);
            }
        }

        @Override
        public <T> T get(@NonNull Object key, Class<T> type) {
            if (tracker.shouldSkipRedis()) {
                return null;
            }
            try {
                var value = delegate.get(key, type);
                afterSuccess();
                return value;
            } catch (RuntimeException e) {
                return failOpen("get", key, e);
            }
        }

        @Override
        public <T> T get(@NonNull Object key, @NonNull Callable<T> loader) {
            if (tracker.shouldSkipRedis()) {
                return load(key, loader);
            }
            var computed = new Object[1];
            Callable<T> tracking = () -> {
                T value = loader.call();
                computed[0] = value;
                return value;
            };
            try {
                T value = delegate.get(key, tracking);
                afterSuccess();
                return value;
            } catch (RuntimeException e) {
                // Falha do loader chega embrulhada em ValueRetrievalException e propaga como hoje
                // (o AvailableSlotsService desembrulha). Só falha do Redis cai para a origem.
                if (e instanceof Cache.ValueRetrievalException || !isCacheFailure(e)) {
                    throw e;
                }
                tracker.onFailure("get", key, e);
                if (computed[0] != null) {
                    // O loader já rodou e só gravar no Redis falhou: não recalcula.
                    @SuppressWarnings("unchecked")
                    T value = (T) computed[0];
                    return value;
                }
                return load(key, loader);
            }
        }

        private <T> T load(Object key, Callable<T> loader) {
            try {
                return loader.call();
            } catch (Exception ex) {
                throw new Cache.ValueRetrievalException(key, loader, ex);
            }
        }

        @Override
        public void put(@NonNull Object key, Object value) {
            if (tracker.shouldSkipRedis()) {
                return;
            }
            try {
                delegate.put(key, value);
                afterSuccess();
            } catch (RuntimeException e) {
                failOpen("put", key, e);
            }
        }

        @Override
        public ValueWrapper putIfAbsent(@NonNull Object key, Object value) {
            if (tracker.shouldSkipRedis()) {
                return null;
            }
            try {
                var existing = delegate.putIfAbsent(key, value);
                afterSuccess();
                return existing;
            } catch (RuntimeException e) {
                return failOpen("putIfAbsent", key, e);
            }
        }

        @Override
        public void evict(@NonNull Object key) {
            if (tracker.shouldSkipRedis()) {
                return;
            }
            try {
                delegate.evict(key);
                afterSuccess();
            } catch (RuntimeException e) {
                failOpen("evict", key, e);
            }
        }

        @Override
        public void clear() {
            if (tracker.shouldSkipRedis()) {
                return;
            }
            try {
                delegate.clear();
                tracker.onSuccess();
            } catch (RuntimeException e) {
                failOpen("clear", "*", e);
            }
        }

        private void afterSuccess() {
            if (tracker.onSuccess()) {
                try {
                    delegate.clear();
                } catch (RuntimeException e) {
                    failOpen("clear", "*", e);
                }
            }
        }

        private <R> R failOpen(String operation, Object key, RuntimeException e) {
            if (!isCacheFailure(e)) {
                throw e;
            }
            tracker.onFailure(operation, key, e);
            return null;
        }
    }
}
