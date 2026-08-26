package io.polity4j.cost.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface defining operations for underlying cache storage implementations
 * (e.g., In-Memory, Caffeine, Redis).
 */
public interface CacheStore {

    Optional<CacheEntry> get(CacheKey key);

    void put(CacheKey key, CacheEntry entry);

    void put(CacheKey key, CacheEntry entry, Duration ttl);

    void invalidate(CacheKey key);

    void invalidateAll();

    default int size() {
        return -1;
    }
}
