package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;

/**
 * Listener interface for cache events.
 *
 * Implement this to observe cache hits and misses —
 * useful for logging, Micrometer metrics, and the SaaS
 * cost savings dashboard.
 *
 * Default no-op implementations provided so you only
 * override what you care about.
 */
public interface CacheEventListener {

    default void onCacheHit(LlmRequest request, CacheEntry entry) {}

    default void onCacheMiss(LlmRequest request, LlmResponse response) {}

    static CacheEventListener noOp() {
        return new CacheEventListener() {};
    }
}
