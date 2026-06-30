package io.polity4j.cost.cache;

import io.polity4j.core.LlmResponse;

import java.time.Instant;
import java.util.Objects;

/**
 * A single cached response with metadata.
 *
 * Immutable. Stored in the cache keyed by CacheKey.
 * cachedAt is recorded for observability — the SaaS dashboard
 * uses it to show cache age and hit patterns.
 */
public record CacheEntry(
        LlmResponse response,
        Instant cachedAt
) {
    public CacheEntry {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(cachedAt, "cachedAt must not be null");
    }

    public static CacheEntry of(LlmResponse response) {
        return new CacheEntry(response, Instant.now());
    }
}
