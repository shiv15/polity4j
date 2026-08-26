package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheStoreTest {

    @Test
    void testCaffeineCacheStoreOperations() {
        CaffeineCacheStore store = CaffeineCacheStore.withTtlAndMaxSize(Duration.ofMinutes(5), 100);
        LlmRequest request = LlmRequest.builder("Caffeine Test", "gpt-4o").build();
        CacheKey key = CacheKey.from(request);
        CacheEntry entry = CacheEntry.of(LlmResponse.builder("Response", "gpt-4o", "mock").build());

        assertThat(store.get(key)).isEmpty();
        store.put(key, entry);

        assertThat(store.get(key)).isPresent();
        assertThat(store.get(key).get().response().content()).isEqualTo("Response");

        store.invalidate(key);
        assertThat(store.get(key)).isEmpty();
    }

    @Test
    void testPerEntryCustomTtlExpiration() throws InterruptedException {
        // Store created with no global TTL
        CaffeineCacheStore store = new CaffeineCacheStore();
        LlmRequest request = LlmRequest.builder("Per Entry Test", "gpt-4o").build();
        CacheKey key = CacheKey.from(request);
        CacheEntry entry = CacheEntry.of(LlmResponse.builder("Response", "gpt-4o", "mock").build());

        // Put entry with custom 50ms TTL
        store.put(key, entry, Duration.ofMillis(50));
        assertThat(store.get(key)).isPresent();

        // Wait for TTL to expire
        Thread.sleep(80);
        assertThat(store.get(key)).isEmpty();
    }
}
