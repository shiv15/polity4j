package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exact match cache — returns a cached response when the request
 * has been seen before, skipping the API call entirely.
 *
 * Delegated storage provider: CacheStore (e.g. InMemoryCacheStore, CaffeineCacheStore).
 */
public final class ExactCacheModule implements PipelineModule {

    private final CacheStore store;
    private final CacheEventListener listener;
    private final Duration defaultTtl;

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public ExactCacheModule() {
        this(new InMemoryCacheStore(), CacheEventListener.noOp(), null);
    }

    public ExactCacheModule(CacheStore store) {
        this(store, CacheEventListener.noOp(), null);
    }

    public ExactCacheModule(CacheEventListener listener) {
        this(new InMemoryCacheStore(), listener, null);
    }

    public ExactCacheModule(CacheStore store, CacheEventListener listener) {
        this(store, listener, null);
    }

    public ExactCacheModule(CacheStore store, CacheEventListener listener, Duration defaultTtl) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.defaultTtl = defaultTtl;
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {

        CacheKey key = CacheKey.from(request);
        Optional<CacheEntry> entryOpt = store.get(key);

        if (entryOpt.isPresent()) {
            CacheEntry entry = entryOpt.get();
            hits.incrementAndGet();
            listener.onCacheHit(request, entry);
            return entry.response();
        }

        // Cache miss — call the real pipeline
        misses.incrementAndGet();
        LlmResponse response = next.proceed(request);

        // Store for future hits
        CacheEntry entry = CacheEntry.of(response);
        if (defaultTtl != null) {
            store.put(key, entry, defaultTtl);
        } else {
            store.put(key, entry);
        }
        listener.onCacheMiss(request, response);

        return response;
    }

    @Override
    public String name() { return "exact-cache"; }

    /** Remove a specific entry — useful when the underlying data changes */
    public void invalidate(LlmRequest request) {
        store.invalidate(CacheKey.from(request));
    }

    /** Remove all entries */
    public void invalidateAll() {
        store.invalidateAll();
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public int size() { return store.size(); }
    public CacheStore store() { return store; }

    /** Hit rate as a value between 0.0 and 1.0 */
    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
}
