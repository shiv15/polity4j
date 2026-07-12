package io.polity4j.quality.context;

import io.polity4j.core.LlmRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Zero-dependency summarizer that builds a synthetic summary
 * from message content without making any AI calls.
 *
 * Output format:
 *   [Summary of earlier conversation: user: first 100 chars...,
 *    assistant: first 100 chars..., user: first 100 chars...]
 *
 * This is intentionally simple — it preserves the shape and
 * topic of the conversation without semantic compression.
 * Users who need true semantic summarization should implement
 * HistorySummarizer with an AI-backed approach.
 *
 * The output role is "system" so the model can distinguish
 * synthetic summary messages from real conversation turns.
 */
public final class DeterministicSummarizer implements HistorySummarizer {

    private static final int MAX_CONTENT_CHARS = 100;

    @Override
    public LlmRequest.Message summarize(List<LlmRequest.Message> messages) {
        if (messages.isEmpty()) {
            return new LlmRequest.Message("system",
                    "[Summary of earlier conversation: no messages]");
        }

        String summary = messages.stream()
                .map(m -> m.role() + ": " + truncate(m.content()))
                .collect(Collectors.joining(", "));

        return new LlmRequest.Message(
                "system",
                "[Summary of earlier conversation: " + summary + "]");
    }

    private String truncate(String content) {
        if (content.length() <= MAX_CONTENT_CHARS) return content;
        return content.substring(0, MAX_CONTENT_CHARS) + "...";
    }
}
