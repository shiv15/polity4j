package io.polity4j.cost.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Production-grade CacheStore backed by Caffeine.
 */
public final class CaffeineCacheStore implements CacheStore {

    private final Cache<CacheKey, CacheEntry> caffeineCache;

    public CaffeineCacheStore() {
        this(Caffeine.newBuilder().maximumSize(10_000).build());
    }

    public CaffeineCacheStore(Cache<CacheKey, CacheEntry> caffeineCache) {
        this.caffeineCache = Objects.requireNonNull(caffeineCache, "caffeineCache must not be null");
    }

    public static CaffeineCacheStore withTtlAndMaxSize(Duration ttl, long maxSize) {
        var builder = Caffeine.newBuilder().maximumSize(maxSize);
        if (ttl != null) {
            builder.expireAfterWrite(ttl);
        }
        return new CaffeineCacheStore(builder.build());
    }

    @Override
    public Optional<CacheEntry> get(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(caffeineCache.getIfPresent(key));
    }

    @Override
    public void put(CacheKey key, CacheEntry entry) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        caffeineCache.put(key, entry);
    }

    @Override
    public void put(CacheKey key, CacheEntry entry, Duration ttl) {
        put(key, entry);
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
}
