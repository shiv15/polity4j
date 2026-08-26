package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCacheStoreTest {

    @Test
    void testPutGetAndInvalidate() {
        InMemoryCacheStore store = new InMemoryCacheStore();
        LlmRequest request = LlmRequest.builder("Hello", "gpt-4o").build();
        CacheKey key = CacheKey.from(request);
        CacheEntry entry = CacheEntry.of(LlmResponse.builder("World", "gpt-4o", "mock").build());

        assertThat(store.get(key)).isEmpty();
        store.put(key, entry);

        assertThat(store.get(key)).isPresent();
        assertThat(store.get(key).get().response().content()).isEqualTo("World");

        store.invalidate(key);
        assertThat(store.get(key)).isEmpty();
    }

    @Test
    void testTtlExpiration() throws InterruptedException {
        InMemoryCacheStore store = new InMemoryCacheStore();
        LlmRequest request = LlmRequest.builder("Hello", "gpt-4o").build();
        CacheKey key = CacheKey.from(request);
        CacheEntry entry = CacheEntry.of(LlmResponse.builder("World", "gpt-4o", "mock").build());

        store.put(key, entry, Duration.ofMillis(50));
        assertThat(store.get(key)).isPresent();

        Thread.sleep(80);
        assertThat(store.get(key)).isEmpty();
    }
}
