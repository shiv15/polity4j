package io.polity4j.quality.prompt;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptOptimizerModuleTest {

    private static final String MODEL = "claude-3-5-sonnet-20241022";

    private static LlmResponse ok(String model) {
        return LlmResponse.builder("ok", model, "anthropic")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    // Captures the actual request received by the client
    private static class CapturingChain implements PipelineChain {
        LlmRequest captured;

        @Override
        public LlmResponse proceed(LlmRequest request) {
            this.captured = request;
            return ok(request.model());
        }
    }

    // ------------------------------------------------------------------
    // Whitespace normalization
    // ------------------------------------------------------------------

    @Test
    void normalizesMultipleBlankLines() {
        var config = PromptOptimizerConfig.builder()
                .normalizeWhitespace(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder(
                "First paragraph\n\n\n\nSecond paragraph", MODEL).build();
        module.process(request, chain);

        assertThat(chain.captured.prompt())
                .isEqualTo("First paragraph\n\nSecond paragraph");
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        var config = PromptOptimizerConfig.builder()
                .normalizeWhitespace(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder(
                "   hello world   ", MODEL).build();
        module.process(request, chain);

        assertThat(chain.captured.prompt()).isEqualTo("hello world");
    }

    @Test
    void doesNotNormalizeWhenDisabled() {
        var config = PromptOptimizerConfig.builder()
                .normalizeWhitespace(false)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        String prompt = "   lots   of   spaces   \n\n\n\n";
        var request = LlmRequest.builder(prompt, MODEL).build();
        module.process(request, chain);

        assertThat(chain.captured.prompt()).isEqualTo(prompt);
    }

    // ------------------------------------------------------------------
    // History deduplication
    // ------------------------------------------------------------------

    @Test
    void removesDuplicateHistoryMessagesKeepingLatest() {
        var config = PromptOptimizerConfig.builder()
                .deduplicateHistory(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var history = List.of(
                new LlmRequest.Message("user", "hello"),
                new LlmRequest.Message("assistant", "hi"),
                new LlmRequest.Message("user", "hello"), // duplicate, keep this latest one
                new LlmRequest.Message("assistant", "hi again"));

        var request = LlmRequest.builder("next", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        assertThat(chain.captured.conversationHistory()).hasSize(3);
        assertThat(chain.captured.conversationHistory().get(0).content())
                .isEqualTo("hi");
        assertThat(chain.captured.conversationHistory().get(1).content())
                .isEqualTo("hello");
        assertThat(chain.captured.conversationHistory().get(2).content())
                .isEqualTo("hi again");
    }

    @Test
    void preservesOrderAfterDeduplicationKeepingLatest() {
        var config = PromptOptimizerConfig.builder()
                .deduplicateHistory(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var history = List.of(
                new LlmRequest.Message("user", "a"),
                new LlmRequest.Message("user", "b"),
                new LlmRequest.Message("user", "a")); // duplicate of first, keep this latest one

        var request = LlmRequest.builder("next", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        var result = chain.captured.conversationHistory();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("b");
        assertThat(result.get(1).content()).isEqualTo("a");
    }

    @Test
    void sameContentDifferentRoleIsNotDuplicate() {
        var config = PromptOptimizerConfig.builder()
                .deduplicateHistory(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var history = List.of(
                new LlmRequest.Message("user", "hello"),
                new LlmRequest.Message("assistant", "hello")); // same content, different role

        var request = LlmRequest.builder("next", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        assertThat(chain.captured.conversationHistory()).hasSize(2);
    }

    // ------------------------------------------------------------------
    // History truncation
    // ------------------------------------------------------------------

    @Test
    void truncatesHistoryFromFrontWhenOverLimit() {
        var config = PromptOptimizerConfig.builder()
                .maxPromptChars(50)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        // "prompt" = 6 chars
        // Each history message role+content = roughly 10+ chars
        var history = List.of(
                new LlmRequest.Message("user", "message one"),   // oldest
                new LlmRequest.Message("assistant", "reply one"),
                new LlmRequest.Message("user", "message two"),
                new LlmRequest.Message("assistant", "reply two")); // newest

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        // Oldest messages should be dropped, newest preserved
        List<LlmRequest.Message> result = chain.captured.conversationHistory();
        assertThat(result).isNotEmpty();
        assertThat(result.get(result.size() - 1).content())
                .isEqualTo("reply two");
    }

    @Test
    void doesNotTruncateWhenUnderLimit() {
        var config = PromptOptimizerConfig.builder()
                .maxPromptChars(10_000)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var history = List.of(
                new LlmRequest.Message("user", "hi"),
                new LlmRequest.Message("assistant", "hello"));

        var request = LlmRequest.builder("short", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        assertThat(chain.captured.conversationHistory()).hasSize(2);
    }

    @Test
    void clearsAllHistoryWhenPromptAloneExceedsLimit() {
        var config = PromptOptimizerConfig.builder()
                .maxPromptChars(5)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var history = List.of(
                new LlmRequest.Message("user", "hi"),
                new LlmRequest.Message("assistant", "hello"));

        // Prompt is longer than the limit
        var request = LlmRequest.builder("this prompt is too long", MODEL)
                .conversationHistory(history).build();
        module.process(request, chain);

        assertThat(chain.captured.conversationHistory()).isEmpty();
    }

    // ------------------------------------------------------------------
    // No modification passthrough
    // ------------------------------------------------------------------

    @Test
    void doesNotModifyRequestWhenNothingToDo() {
        var module = new PromptOptimizerModule(PromptOptimizerConfig.DEFAULT);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("clean prompt", MODEL)
                .callerId("service-a")
                .regionContext("eu-west-1")
                .build();
        module.process(request, chain);

        // Exact same reference — no new object created
        assertThat(chain.captured).isSameAs(request);
    }

    @Test
    void preservesAllFieldsWhenModifying() {
        var config = PromptOptimizerConfig.builder()
                .normalizeWhitespace(true)
                .build();
        var module = new PromptOptimizerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("  hello  ", MODEL)
                .maxTokens(512)
                .callerId("service-a")
                .regionContext("eu-west-1")
                .build();
        module.process(request, chain);

        assertThat(chain.captured.prompt()).isEqualTo("hello");
        assertThat(chain.captured.model()).isEqualTo(MODEL);
        assertThat(chain.captured.maxTokens()).isEqualTo(512);
        assertThat(chain.captured.callerId()).isEqualTo("service-a");
        assertThat(chain.captured.regionContext()).isEqualTo("eu-west-1");
    }

    // ------------------------------------------------------------------
    // Name
    // ------------------------------------------------------------------

    @Test
    void nameIsCorrect() {
        assertThat(new PromptOptimizerModule().name())
                .isEqualTo("prompt-optimizer");
    }
}
