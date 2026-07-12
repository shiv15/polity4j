package io.polity4j.quality.context;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages long conversations approaching the model's context window.
 *
 * When total history size exceeds maxHistoryChars:
 *   1. Keep the most recent windowSize messages intact
 *   2. Take all messages older than the window
 *   3. Pass them to HistorySummarizer to produce one summary message
 *   4. Replace old messages with the summary
 *   5. Result: [summary message] + [last windowSize messages]
 *
 * Key difference from PromptOptimizerModule:
 *   PromptOptimizer truncates — drops oldest messages, loses information
 *   ContextWindowManager summarizes — condenses, preserves meaning
 *
 * If history is within the limit, the original request is passed
 * through unchanged — no allocation, no summarizer call.
 *
 * Only conversationHistory is modified — prompt is never touched.
 */
public final class ContextWindowManagerModule implements PipelineModule {

    private final ContextWindowManagerConfig config;

    public ContextWindowManagerModule() {
        this(ContextWindowManagerConfig.DEFAULT);
    }

    public ContextWindowManagerModule(ContextWindowManagerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");

        List<LlmRequest.Message> history = request.conversationHistory();

        if (!needsCondensing(history)) {
            // Under the limit — pass through unchanged, no allocation
            return next.proceed(request);
        }

        List<LlmRequest.Message> condensed = condense(history);

        LlmRequest optimized = LlmRequest.builder(
                        request.prompt(), request.model())
                .maxTokens(request.maxTokens())
                .callerId(request.callerId())
                .regionContext(request.regionContext())
                .conversationHistory(condensed)
                .build();

        return next.proceed(optimized);
    }

    @Override
    public String name() { return "context-window-manager"; }

    // -------------------------------------------------------------------------
    // Internal logic
    // -------------------------------------------------------------------------

    private boolean needsCondensing(List<LlmRequest.Message> history) {
        if (history.isEmpty()) return false;

        int totalChars = history.stream()
                .mapToInt(m -> m.role().length() + m.content().length())
                .sum();

        return totalChars > config.maxHistoryChars();
    }

    /**
     * Condenses history into [summary] + [recent window].
     *
     * If history has windowSize or fewer messages, everything is
     * recent — nothing to summarize. In practice needsCondensing()
     * would have returned false before reaching here for small
     * histories, but we guard anyway.
     */
    private List<LlmRequest.Message> condense(List<LlmRequest.Message> history) {
        int windowSize = Math.min(config.windowSize(), history.size());
        int oldMessageCount = history.size() - windowSize;

        if (oldMessageCount <= 0) {
            // Nothing old enough to summarize
            return history;
        }

        List<LlmRequest.Message> oldMessages = history.subList(0, oldMessageCount);
        List<LlmRequest.Message> recentMessages = history.subList(oldMessageCount, history.size());

        LlmRequest.Message summary = config.summarizer().summarize(oldMessages);

        List<LlmRequest.Message> result = new ArrayList<>();
        result.add(summary);
        result.addAll(recentMessages);

        return result;
    }
}
