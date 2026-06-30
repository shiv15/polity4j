# Exact Cache

The `ExactCacheModule` is an in-memory, thread-safe pipeline module that caches responses for identical LLM requests to eliminate duplicate API charges and reduce overall cost.

## How It Works

1. **Cache Key Calculation**: For every incoming `LlmRequest`, the module computes a SHA-256 hash using the content-bearing fields:
   - `model` (same prompt to different models yields different responses)
   - `prompt` (the literal user instruction)
   - `conversationHistory` (prior chat context)
   
   Metadata and governance fields like `callerId` and `regionContext` are deliberately excluded from the hash to maximize cache reuse across different callers.
   
2. **Short-Circuiting**: 
   - **On Cache Hit**: Returns the cached response immediately, skipping the downstream LLM client entirely.
   - **On Cache Miss**: Executes the downstream pipeline, caches the successful response, and returns it.

## Limitations in Multi-Turn Chats

> [!WARNING]
> Because `conversationHistory` is factored into the cache key to guarantee response correctness and safety, **the exact cache is highly ineffective for dynamic, multi-turn conversation threads**. 
>
> As a conversation progresses, the history grows with each turn, guaranteeing that subsequent turns produce completely unique cache keys. 
> 
> **Best Use Cases**:
> - Single-shot stateless calls (e.g. classification, entity extraction, summarization, tool-routing).
> - Deterministic integration test scripts or local mock runs.
> - Batch workloads executing the same prompt templates repeatedly over recurring inputs.

## Configuration

You can instantiate `ExactCacheModule` with an optional `CacheEventListener` to trace cache hits and misses:

```java
import io.polity4j.cost.cache.ExactCacheModule;
import io.polity4j.cost.cache.CacheEventListener;
import io.polity4j.cost.cache.CacheEntry;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;

CacheEventListener listener = new CacheEventListener() {
    @Override
    public void onCacheHit(LlmRequest request, CacheEntry entry) {
        System.out.println("Cache Hit for prompt: " + request.prompt());
    }

    @Override
    public void onCacheMiss(LlmRequest request, LlmResponse response) {
        System.out.println("Cache Miss for prompt: " + request.prompt());
    }
};

ExactCacheModule cacheModule = new ExactCacheModule(listener);
```

## Manual Invalidation

If your underlying data changes and you need to purge the cache, you can invalidate specific requests or clear the entire cache:

```java
// Invalidate a specific request
cacheModule.invalidate(request);

// Clear everything
cacheModule.invalidateAll();
```

## Metrics

The module tracks hits and misses internally:

```java
long hits = cacheModule.hits();
long misses = cacheModule.misses();
double hitRate = cacheModule.hitRate(); // returns a value between 0.0 and 1.0
int size = cacheModule.size();
```
