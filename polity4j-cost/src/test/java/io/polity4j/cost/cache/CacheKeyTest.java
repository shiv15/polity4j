package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyTest {

    @Test
    void sameRequestProducesSameKey() {
        var request1 = LlmRequest.builder("Hello", "gpt-4o").build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o").build();

        assertThat(CacheKey.from(request1)).isEqualTo(CacheKey.from(request2));
    }

    @Test
    void differentPromptProducesDifferentKey() {
        var request1 = LlmRequest.builder("Hello", "gpt-4o").build();
        var request2 = LlmRequest.builder("Goodbye", "gpt-4o").build();

        assertThat(CacheKey.from(request1))
                .isNotEqualTo(CacheKey.from(request2));
    }

    @Test
    void differentModelProducesDifferentKey() {
        var request1 = LlmRequest.builder("Hello", "gpt-4o").build();
        var request2 = LlmRequest.builder("Hello", "claude-3-5-sonnet-20241022").build();

        assertThat(CacheKey.from(request1))
                .isNotEqualTo(CacheKey.from(request2));
    }

    @Test
    void differentConversationHistoryProducesDifferentKey() {
        var history1 = List.of(
                new LlmRequest.Message("user", "I love dogs"),
                new LlmRequest.Message("assistant", "Dogs are great"));
        var history2 = List.of(
                new LlmRequest.Message("user", "I love cats"),
                new LlmRequest.Message("assistant", "Cats are great"));

        var request1 = LlmRequest.builder("What pet should I get?", "gpt-4o")
                .conversationHistory(history1).build();
        var request2 = LlmRequest.builder("What pet should I get?", "gpt-4o")
                .conversationHistory(history2).build();

        assertThat(CacheKey.from(request1))
                .isNotEqualTo(CacheKey.from(request2));
    }

    @Test
    void samePromptDifferentCallerIdProducesSameKey() {
        // callerId is a governance field — not part of the cache key
        var request1 = LlmRequest.builder("Hello", "gpt-4o")
                .callerId("service-a").build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o")
                .callerId("service-b").build();

        assertThat(CacheKey.from(request1)).isEqualTo(CacheKey.from(request2));
    }

    @Test
    void samePromptDifferentRegionProducesSameKey() {
        // regionContext is a governance field — not part of the cache key
        var request1 = LlmRequest.builder("Hello", "gpt-4o")
                .regionContext("eu-west-1").build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o")
                .regionContext("us-east-1").build();

        assertThat(CacheKey.from(request1)).isEqualTo(CacheKey.from(request2));
    }

    @Test
    void emptyHistoryAndNoHistoryProduceSameKey() {
        var request1 = LlmRequest.builder("Hello", "gpt-4o")
                .conversationHistory(List.of()).build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o").build();

        assertThat(CacheKey.from(request1)).isEqualTo(CacheKey.from(request2));
    }

    @Test
    void historyOrderMatters() {
        // Same messages, different order — should produce different keys
        var history1 = List.of(
                new LlmRequest.Message("user", "first"),
                new LlmRequest.Message("assistant", "second"));
        var history2 = List.of(
                new LlmRequest.Message("assistant", "second"),
                new LlmRequest.Message("user", "first"));

        var request1 = LlmRequest.builder("Hello", "gpt-4o")
                .conversationHistory(history1).build();
        var request2 = LlmRequest.builder("Hello", "gpt-4o")
                .conversationHistory(history2).build();

        assertThat(CacheKey.from(request1))
                .isNotEqualTo(CacheKey.from(request2));
    }

    @Test
    void keyValueIsSha256HexString() {
        var request = LlmRequest.builder("Hello", "gpt-4o").build();
        String value = CacheKey.from(request).value();

        // SHA-256 produces 64 hex characters
        assertThat(value).hasSize(64);
        assertThat(value).matches("[0-9a-f]{64}");
    }
}
