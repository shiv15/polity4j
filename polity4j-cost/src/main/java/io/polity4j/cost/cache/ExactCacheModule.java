package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exact match cache — returns a cached response when the request
 * has been seen before, skipping the API call entirely.
 *
 * Cache key: SHA-256(model + prompt + conversationHistory)
 * See CacheKey for field inclusion rationale.
 *
 * Thread safety: ConcurrentHashMap for the store, AtomicLong for
 * hit/miss counters. All operations are lock-free.
 *
 * This is an in-memory cache — entries are lost on restart.
 * The SaaS platform provides a distributed persistent cache
 * for multi-instance deployments.
 *
 * On a cache hit:
 *   - Returns the cached LlmResponse immediately
 *   - Does NOT call next — the API call is skipped entirely
 *   - Fires CacheEventListener.onCacheHit() for observability
 *
 * On a cache miss:
 *   - Calls next to get a real response
 *   - Stores the response in the cache for future hits
 *   - Fires CacheEventListener.onCacheMiss() for observability
 */
public final class ExactCacheModule implements PipelineModule {

    private final ConcurrentHashMap<CacheKey, CacheEntry> store =
            new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    private final CacheEventListener listener;

    public ExactCacheModule() {
        this(CacheEventListener.noOp());
    }

    public ExactCacheModule(CacheEventListener listener) {
        this.listener = listener;
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {

        CacheKey key = CacheKey.from(request);
        CacheEntry entry = store.get(key);

        if (entry != null) {
            hits.incrementAndGet();
            listener.onCacheHit(request, entry);
            return entry.response();
        }

        // Cache miss — call the real pipeline
        misses.incrementAndGet();
        LlmResponse response = next.proceed(request);

        // Store for future hits
        store.put(key, CacheEntry.of(response));
        listener.onCacheMiss(request, response);

        return response;
    }

    @Override
    public String name() { return "exact-cache"; }

    /** Remove a specific entry — useful when the underlying data changes */
    public void invalidate(LlmRequest request) {
        store.remove(CacheKey.from(request));
    }

    /** Remove all entries */
    public void invalidateAll() {
        store.clear();
    }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public int size() { return store.size(); }

    /** Hit rate as a value between 0.0 and 1.0 */
    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
}
