package io.polity4j.cost.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory CacheStore backed by ConcurrentHashMap with optional TTL expiration support.
 */
public final class InMemoryCacheStore implements CacheStore {

    private record ExpiringEntry(CacheEntry entry, Long expiresAtMs) {
        boolean isExpired(long now) {
            return expiresAtMs != null && now > expiresAtMs;
        }
    }

    private final ConcurrentHashMap<CacheKey, ExpiringEntry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<CacheEntry> get(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        ExpiringEntry item = store.get(key);
        if (item == null) {
            return Optional.empty();
        }
        if (item.isExpired(System.currentTimeMillis())) {
            store.remove(key, item);
            return Optional.empty();
        }
        return Optional.of(item.entry());
    }

    @Override
    public void put(CacheKey key, CacheEntry entry) {
        put(key, entry, null);
    }

    @Override
    public void put(CacheKey key, CacheEntry entry, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        Long expiresAt = ttl != null ? System.currentTimeMillis() + ttl.toMillis() : null;
        store.put(key, new ExpiringEntry(entry, expiresAt));
    }

    @Override
    public void invalidate(CacheKey key) {
        if (key != null) {
            store.remove(key);
        }
    }

    @Override
    public void invalidateAll() {
        store.clear();
    }

    @Override
    public int size() {
        // Clean up expired entries before size check
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().isExpired(now));
        return store.size();
    }
}
