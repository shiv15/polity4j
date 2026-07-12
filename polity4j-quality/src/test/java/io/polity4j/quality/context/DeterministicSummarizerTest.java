package io.polity4j.quality.context;

import io.polity4j.core.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicSummarizerTest {

    private final DeterministicSummarizer summarizer = new DeterministicSummarizer();

    @Test
    void producesSingleMessageFromMultipleMessages() {
        var messages = List.of(
                new LlmRequest.Message("user", "hello"),
                new LlmRequest.Message("assistant", "hi there"),
                new LlmRequest.Message("user", "how are you?"));

        var result = summarizer.summarize(messages);

        assertThat(result).isNotNull();
    }

    @Test
    void outputRoleIsSystem() {
        var messages = List.of(
                new LlmRequest.Message("user", "hello"));

        var result = summarizer.summarize(messages);

        assertThat(result.role()).isEqualTo("system");
    }

    @Test
    void contentContainsEachMessageRole() {
        var messages = List.of(
                new LlmRequest.Message("user", "hello"),
                new LlmRequest.Message("assistant", "hi"));

        var result = summarizer.summarize(messages);

        assertThat(result.content()).contains("user");
        assertThat(result.content()).contains("assistant");
    }

    @Test
    void contentContainsTruncatedMessageContent() {
        var messages = List.of(
                new LlmRequest.Message("user", "hello world"));

        var result = summarizer.summarize(messages);

        assertThat(result.content()).contains("hello world");
    }

    @Test
    void truncatesLongMessageContentTo100Chars() {
        String longContent = "a".repeat(200);
        var messages = List.of(
                new LlmRequest.Message("user", longContent));

        var result = summarizer.summarize(messages);

        // Should contain truncated version with ellipsis
        assertThat(result.content()).contains("a".repeat(100) + "...");
        assertThat(result.content()).doesNotContain("a".repeat(101));
    }

    @Test
    void handlesEmptyListGracefully() {
        var result = summarizer.summarize(List.of());

        assertThat(result).isNotNull();
        assertThat(result.role()).isEqualTo("system");
        assertThat(result.content()).contains("no messages");
    }

    @Test
    void contentStartsWithSummaryPrefix() {
        var messages = List.of(
                new LlmRequest.Message("user", "hello"));

        var result = summarizer.summarize(messages);

        assertThat(result.content())
                .startsWith("[Summary of earlier conversation:");
    }
}
