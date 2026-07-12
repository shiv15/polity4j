package io.polity4j.quality.context;

import io.polity4j.core.LlmRequest;

import java.util.List;

/**
 * Strategy interface for summarizing conversation history.
 *
 * Takes a list of messages that are beyond the context window
 * and produces a single condensed message that replaces them.
 *
 * The default implementation is DeterministicSummarizer — zero
 * dependencies, no AI call, builds a synthetic summary from
 * message content deterministically.
 *
 * Users who need higher quality summarization can implement this
 * interface with an AI-based approach — the module does not care
 * how the summary is produced.
 */
@FunctionalInterface
public interface HistorySummarizer {

    /**
     * Summarize a list of messages into a single message.
     *
     * @param messages the messages to summarize — never empty
     * @return a single message representing the condensed history.
     *         Role should be "system" to distinguish it from real
     *         conversation turns.
     */
    LlmRequest.Message summarize(List<LlmRequest.Message> messages);
}
