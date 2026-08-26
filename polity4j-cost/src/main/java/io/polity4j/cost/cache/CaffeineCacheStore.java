package io.polity4j.cost.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Production-grade CacheStore backed by Caffeine with support for global and per-entry TTL expiration.
 */
public final class CaffeineCacheStore implements CacheStore {

    private record CaffeineHolder(CacheEntry entry, Duration customTtl) {}

    private final Cache<CacheKey, CaffeineHolder> caffeineCache;
    private final Duration defaultTtl;

    public CaffeineCacheStore() {
        this(null, 10_000);
    }

    public CaffeineCacheStore(Duration defaultTtl, long maxSize) {
        var builder = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new Expiry<CacheKey, CaffeineHolder>() {
                    @Override
                    public long expireAfterCreate(CacheKey key, CaffeineHolder value, long currentTime) {
                        Duration ttlToUse = value.customTtl() != null ? value.customTtl() : defaultTtl;
                        return ttlToUse != null ? ttlToUse.toNanos() : Long.MAX_VALUE;
                    }

                    @Override
                    public long expireAfterUpdate(CacheKey key, CaffeineHolder value, long currentTime, long currentDuration) {
                        Duration ttlToUse = value.customTtl() != null ? value.customTtl() : defaultTtl;
                        return ttlToUse != null ? ttlToUse.toNanos() : currentDuration;
                    }

                    @Override
                    public long expireAfterRead(CacheKey key, CaffeineHolder value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                });
        this.caffeineCache = builder.build();
        this.defaultTtl = defaultTtl;
    }

    public static CaffeineCacheStore withTtlAndMaxSize(Duration ttl, long maxSize) {
        return new CaffeineCacheStore(ttl, maxSize);
    }

    @Override
    public Optional<CacheEntry> get(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        CaffeineHolder holder = caffeineCache.getIfPresent(key);
        return holder != null ? Optional.of(holder.entry()) : Optional.empty();
    }

    @Override
    public void put(CacheKey key, CacheEntry entry) {
        put(key, entry, null);
    }

    @Override
    public void put(CacheKey key, CacheEntry entry, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        caffeineCache.put(key, new CaffeineHolder(entry, ttl));
    }

    @Override
    public void invalidate(CacheKey key) {
        if (key != null) {
            caffeineCache.invalidate(key);
        }
    }

    @Override
    public void invalidateAll() {
        caffeineCache.invalidateAll();
    }

    @Override
    public int size() {
        caffeineCache.cleanUp();
        return (int) caffeineCache.estimatedSize();
    }

    public Duration defaultTtl() {
        return defaultTtl;
    }
}
