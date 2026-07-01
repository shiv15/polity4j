package io.polity4j.quality.prompt;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Optimizes the prompt and conversation history before the API call.
 *
 * Applies optimizations in this order:
 *   1. Normalize whitespace in prompt
 *   2. Deduplicate conversation history (keeps the latest occurrence)
 *   3. Truncate history from the front if total size exceeds maxPromptChars
 *
 * Each optimization is independently configurable. If the request does
 * not need modification, the original request object is passed through
 * unchanged — no unnecessary allocation.
 *
 * This module never changes the meaning of a prompt. It only removes
 * waste. Semantic compression belongs in ContextWindowManagerModule.
 */
public final class PromptOptimizerModule implements PipelineModule {

    private final PromptOptimizerConfig config;

    public PromptOptimizerModule() {
        this(PromptOptimizerConfig.DEFAULT);
    }

    public PromptOptimizerModule(PromptOptimizerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {

        String prompt = request.prompt();
        List<LlmRequest.Message> history =
                new ArrayList<>(request.conversationHistory());
        boolean modified = false;

        // 1. Normalize whitespace in prompt
        if (config.normalizeWhitespace()) {
            String normalized = normalizeWhitespace(prompt);
            if (!normalized.equals(prompt)) {
                prompt = normalized;
                modified = true;
            }
        }

        // 2. Deduplicate conversation history
        if (config.deduplicateHistory()) {
            List<LlmRequest.Message> deduped = deduplicateHistory(history);
            if (deduped.size() != history.size()) {
                history = deduped;
                modified = true;
            }
        }

        // 3. Truncate history from front if total size exceeds limit
        if (config.hasMaxPromptChars()) {
            List<LlmRequest.Message> truncated =
                    truncateHistory(prompt, history, config.maxPromptChars());
            if (truncated.size() != history.size()) {
                history = truncated;
                modified = true;
            }
        }

        // If nothing changed, pass original request through unchanged
        if (!modified) {
            return next.proceed(request);
        }

        LlmRequest optimized = LlmRequest.builder(prompt, request.model())
                .maxTokens(request.maxTokens())
                .callerId(request.callerId())
                .regionContext(request.regionContext())
                .conversationHistory(history)
                .build();

        return next.proceed(optimized);
    }

    @Override
    public String name() { return "prompt-optimizer"; }

    // -------------------------------------------------------------------------
    // Optimization steps
    // -------------------------------------------------------------------------

    /**
     * Collapses multiple consecutive blank lines into one,
     * trims leading and trailing whitespace.
     * Does not touch whitespace within a single line.
     */
    private String normalizeWhitespace(String prompt) {
        // Collapse 3+ newlines into 2
        String collapsed = prompt.replaceAll("\n{3,}", "\n\n");
        return collapsed.strip();
    }

    /**
     * Removes duplicate messages from conversation history.
     * When two messages have identical role and content, the latest
     * occurrence is kept and the earlier duplicate is removed.
     * Order of remaining messages is preserved.
     */
    private List<LlmRequest.Message> deduplicateHistory(
            List<LlmRequest.Message> history) {

        var seen = new LinkedHashSet<String>();
        List<LlmRequest.Message> result = new ArrayList<>();
        
        // Iterate backward to prioritize the latest duplicates
        for (int i = history.size() - 1; i >= 0; i--) {
            LlmRequest.Message message = history.get(i);
            String key = message.role() + '\0' + message.content();
            if (seen.add(key)) {
                result.add(0, message);
            }
        }
        return result;
    }

    /**
     * Truncates conversation history from the front until the total
     * character count of prompt + all history messages fits within limit.
     *
     * Newest messages are always preserved — we drop from the oldest.
     * If even the prompt alone exceeds the limit, history is cleared
     * entirely but the prompt itself is never truncated here.
     */
    private List<LlmRequest.Message> truncateHistory(
            String prompt,
            List<LlmRequest.Message> history,
            int maxChars) {

        int totalChars = prompt.length();
        for (LlmRequest.Message message : history) {
            totalChars += message.role().length()
                    + message.content().length();
        }

        if (totalChars <= maxChars) return history;

        // Drop messages from the front until we fit
        List<LlmRequest.Message> result = new ArrayList<>(history);
        while (!result.isEmpty() && totalChars > maxChars) {
            LlmRequest.Message dropped = result.remove(0);
            totalChars -= dropped.role().length()
                    + dropped.content().length();
        }

        return result;
    }
}
