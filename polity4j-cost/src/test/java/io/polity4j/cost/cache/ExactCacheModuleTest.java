package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExactCacheModuleTest {

    private static final String MODEL = "claude-3-5-sonnet-20241022";

    private static LlmResponse response(String content) {
        return LlmResponse.builder(content, MODEL, "anthropic")
                .inputTokens(10)
                .outputTokens(50)
                .estimatedCost(new BigDecimal("0.001"))
                .build();
    }

    // Chain that counts how many times it is called
    private PipelineChain countingChain(int[] count, String content) {
        return request -> {
            count[0]++;
            return response(content);
        };
    }

    // ------------------------------------------------------------------
    // Cache miss — first call
    // ------------------------------------------------------------------

    @Test
    void cacheMissCallsNext() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request = LlmRequest.builder("Hello", MODEL).build();

        var result = module.process(request, countingChain(count, "response-1"));

        assertThat(result.content()).isEqualTo("response-1");
        assertThat(count[0]).isEqualTo(1);
        assertThat(module.misses()).isEqualTo(1);
        assertThat(module.hits()).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Cache hit — second call with same request
    // ------------------------------------------------------------------

    @Test
    void cacheHitSkipsNextCall() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request = LlmRequest.builder("Hello", MODEL).build();

        // First call — miss, populates cache
        module.process(request, countingChain(count, "response-1"));
        // Second call — hit, next should not be called
        var result = module.process(request, countingChain(count, "response-2"));

        assertThat(result.content()).isEqualTo("response-1");
        assertThat(count[0]).isEqualTo(1); // next called only once
        assertThat(module.hits()).isEqualTo(1);
        assertThat(module.misses()).isEqualTo(1);
    }

    @Test
    void differentPromptsAreCachedSeparately() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request1 = LlmRequest.builder("Hello", MODEL).build();
        var request2 = LlmRequest.builder("Goodbye", MODEL).build();

        module.process(request1, countingChain(count, "response-hello"));
        var result = module.process(request2, countingChain(count, "response-goodbye"));

        assertThat(result.content()).isEqualTo("response-goodbye");
        assertThat(count[0]).isEqualTo(2); // both called next
        assertThat(module.misses()).isEqualTo(2);
    }

    @Test
    void differentModelsAreCachedSeparately() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request1 = LlmRequest.builder("Hello", MODEL).build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o").build();

        module.process(request1, countingChain(count, "anthropic-response"));
        var result = module.process(request2, countingChain(count, "openai-response"));

        assertThat(result.content()).isEqualTo("openai-response");
        assertThat(count[0]).isEqualTo(2);
    }

    @Test
    void differentHistoryAreCachedSeparately() {
        int[] count = {0};
        var module = new ExactCacheModule();

        var history1 = List.of(new LlmRequest.Message("user", "I love dogs"));
        var history2 = List.of(new LlmRequest.Message("user", "I love cats"));

        var request1 = LlmRequest.builder("What pet?", MODEL)
                .conversationHistory(history1).build();
        var request2 = LlmRequest.builder("What pet?", MODEL)
                .conversationHistory(history2).build();

        module.process(request1, countingChain(count, "get a dog"));
        var result = module.process(request2, countingChain(count, "get a cat"));

        assertThat(result.content()).isEqualTo("get a cat");
        assertThat(count[0]).isEqualTo(2);
    }

    @Test
    void samePromptDifferentCallerIdIsACacheHit() {
        int[] count = {0};
        var module = new ExactCacheModule();

        var request1 = LlmRequest.builder("Hello", MODEL)
                .callerId("service-a").build();
        var request2 = LlmRequest.builder("Hello", MODEL)
                .callerId("service-b").build();

        module.process(request1, countingChain(count, "response-1"));
        var result = module.process(request2, countingChain(count, "response-2"));

        // callerId not part of key — should be a hit
        assertThat(result.content()).isEqualTo("response-1");
        assertThat(count[0]).isEqualTo(1);
        assertThat(module.hits()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Invalidation
    // ------------------------------------------------------------------

    @Test
    void invalidateRemovesEntry() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request = LlmRequest.builder("Hello", MODEL).build();

        module.process(request, countingChain(count, "response-1"));
        module.invalidate(request);
        module.process(request, countingChain(count, "response-2"));

        assertThat(count[0]).isEqualTo(2); // next called twice
        assertThat(module.misses()).isEqualTo(2);
    }

    @Test
    void invalidateAllClearsCache() {
        int[] count = {0};
        var module = new ExactCacheModule();

        var request1 = LlmRequest.builder("Hello", MODEL).build();
        var request2 = LlmRequest.builder("Goodbye", MODEL).build();

        module.process(request1, countingChain(count, "r1"));
        module.process(request2, countingChain(count, "r2"));
        assertThat(module.size()).isEqualTo(2);

        module.invalidateAll();
        assertThat(module.size()).isEqualTo(0);

        module.process(request1, countingChain(count, "r1-again"));
        assertThat(count[0]).isEqualTo(3); // called next again after clear
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    @Test
    void hitRateCalculatedCorrectly() {
        int[] count = {0};
        var module = new ExactCacheModule();
        var request = LlmRequest.builder("Hello", MODEL).build();

        // 1 miss then 3 hits = 75% hit rate
        module.process(request, countingChain(count, "r1")); // miss
        module.process(request, countingChain(count, "r1")); // hit
        module.process(request, countingChain(count, "r1")); // hit
        module.process(request, countingChain(count, "r1")); // hit

        assertThat(module.hitRate()).isEqualTo(0.75);
    }

    @Test
    void hitRateIsZeroWithNoCalls() {
        var module = new ExactCacheModule();
        assertThat(module.hitRate()).isEqualTo(0.0);
    }

    // ------------------------------------------------------------------
    // Event listener
    // ------------------------------------------------------------------

    @Test
    void firesHitAndMissEvents() {
        var hitRequests = new ArrayList<String>();
        var missRequests = new ArrayList<String>();

        CacheEventListener listener = new CacheEventListener() {
            @Override
            public void onCacheHit(LlmRequest req, CacheEntry entry) {
                hitRequests.add(req.prompt());
            }
            @Override
            public void onCacheMiss(LlmRequest req, LlmResponse response) {
                missRequests.add(req.prompt());
            }
        };

        var module = new ExactCacheModule(listener);
        var request = LlmRequest.builder("Hello", MODEL).build();

        module.process(request, req -> response("r1")); // miss
        module.process(request, req -> response("r2")); // hit

        assertThat(missRequests).containsExactly("Hello");
        assertThat(hitRequests).containsExactly("Hello");
    }

    // ------------------------------------------------------------------
    // Name
    // ------------------------------------------------------------------

    @Test
    void nameIsCorrect() {
        assertThat(new ExactCacheModule().name()).isEqualTo("exact-cache");
    }
}
