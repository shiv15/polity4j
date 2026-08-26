package io.polity4j.examples.cost;

import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.cost.cache.CaffeineCacheStore;
import io.polity4j.cost.cache.ExactCacheModule;

import java.time.Duration;

public class CaffeineCacheDemo {

    public static void main(String[] args) throws Exception {
        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                System.out.println("-> Executing mock API call for prompt: " + request.prompt());
                return LlmResponse.builder("Capital of France is Paris.", request.model(), provider())
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        // 1. Create Caffeine CacheStore with 500ms TTL and 1000 max size
        CaffeineCacheStore caffeineStore = CaffeineCacheStore.withTtlAndMaxSize(Duration.ofMillis(500), 1000);
        ExactCacheModule cacheModule = new ExactCacheModule(caffeineStore);

        LlmPipeline pipeline = LlmPipeline.builder(mockClient)
                .with(cacheModule)
                .build();

        LlmRequest request = LlmRequest.builder("What is the capital of France?", "gpt-4o")
                .systemPrompt("You are a helpful geography bot.")
                .build();

        // Turn 1: Cache Miss
        System.out.println("First call:");
        pipeline.execute(request);
        System.out.println("Cache Hits: " + cacheModule.hits() + ", Misses: " + cacheModule.misses());

        // Turn 2: Cache Hit (0ms latency, skips API call)
        System.out.println("\nSecond call (immediate):");
        pipeline.execute(request);
        System.out.println("Cache Hits: " + cacheModule.hits() + ", Misses: " + cacheModule.misses());

        // Wait for TTL expiration
        Thread.sleep(600);

        // Turn 3: Cache Miss after TTL expiration
        System.out.println("\nThird call (after TTL expiration):");
        pipeline.execute(request);
        System.out.println("Cache Hits: " + cacheModule.hits() + ", Misses: " + cacheModule.misses());
    }
}
