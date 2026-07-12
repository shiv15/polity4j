package io.polity4j.quality.context;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextWindowManagerModuleTest {

    private static final String MODEL = "claude-3-5-sonnet-20241022";

    private static LlmResponse ok() {
        return LlmResponse.builder("ok", MODEL, "anthropic")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    private static class CapturingChain implements PipelineChain {
        LlmRequest captured;

        @Override
        public LlmResponse proceed(LlmRequest request) throws PolityException {
            this.captured = request;
            return ok();
        }
    }

    // Builds a list of N messages with predictable content
    private static List<LlmRequest.Message> messages(int count) {
        var list = new ArrayList<LlmRequest.Message>();
        for (int i = 0; i < count; i++) {
            list.add(new LlmRequest.Message(
                    i % 2 == 0 ? "user" : "assistant",
                    "message content number " + i));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Pass through when under limit
    // ------------------------------------------------------------------

    @Test
    void passesThroughUnchangedWhenUnderLimit() {
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(10_000)
                .windowSize(6)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(messages(4))
                .build();

        module.process(request, chain);

        // Same reference — no new object created
        assertThat(chain.captured).isSameAs(request);
    }

    @Test
    void passesThroughEmptyHistoryUnchanged() {
        var module = new ContextWindowManagerModule();
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL).build();
        module.process(request, chain);

        assertThat(chain.captured).isSameAs(request);
    }

    // ------------------------------------------------------------------
    // Condensing
    // ------------------------------------------------------------------

    @Test
    void condensesHistoryWhenOverLimit() {
        // Each message is ~30 chars, 10 messages = ~300 chars
        // Set limit to 100 to force condensing
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(4)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(messages(10))
                .build();

        module.process(request, chain);

        // Should have condensed — not the same reference
        assertThat(chain.captured).isNotSameAs(request);
    }

    @Test
    void keepsMostRecentWindowSizeMessages() {
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(3)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var history = messages(10);
        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(history)
                .build();

        module.process(request, chain);

        List<LlmRequest.Message> result = chain.captured.conversationHistory();

        // Last 3 messages should be at the end
        assertThat(result.get(result.size() - 1).content())
                .isEqualTo(history.get(9).content());
        assertThat(result.get(result.size() - 2).content())
                .isEqualTo(history.get(8).content());
        assertThat(result.get(result.size() - 3).content())
                .isEqualTo(history.get(7).content());
    }

    @Test
    void summaryMessageIsFirst() {
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(3)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(messages(10))
                .build();

        module.process(request, chain);

        List<LlmRequest.Message> result = chain.captured.conversationHistory();

        // First message should be the summary
        assertThat(result.get(0).role()).isEqualTo("system");
        assertThat(result.get(0).content())
                .startsWith("[Summary of earlier conversation:");
    }

    @Test
    void resultIsSummaryPlusWindow() {
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(3)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(messages(10))
                .build();

        module.process(request, chain);

        // 1 summary + 3 window = 4 total
        assertThat(chain.captured.conversationHistory()).hasSize(4);
    }

    @Test
    void customSummarizerIsCalled() {
        var called = new ArrayList<List<LlmRequest.Message>>();
        HistorySummarizer customSummarizer = msgs -> {
            called.add(msgs);
            return new LlmRequest.Message("system", "custom summary");
        };

        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(3)
                .summarizer(customSummarizer)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("prompt", MODEL)
                .conversationHistory(messages(10))
                .build();

        module.process(request, chain);

        assertThat(called).hasSize(1);
        assertThat(chain.captured.conversationHistory().get(0).content())
                .isEqualTo("custom summary");
    }

    // ------------------------------------------------------------------
    // Field preservation
    // ------------------------------------------------------------------

    @Test
    void preservesAllFieldsWhenCondensing() {
        var config = ContextWindowManagerConfig.builder()
                .maxHistoryChars(100)
                .windowSize(3)
                .build();
        var module = new ContextWindowManagerModule(config);
        var chain = new CapturingChain();

        var request = LlmRequest.builder("my prompt", MODEL)
                .maxTokens(512)
                .callerId("service-a")
                .regionContext("eu-west-1")
                .conversationHistory(messages(10))
                .build();

        module.process(request, chain);

        assertThat(chain.captured.prompt()).isEqualTo("my prompt");
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
        assertThat(new ContextWindowManagerModule().name())
                .isEqualTo("context-window-manager");
    }
}
