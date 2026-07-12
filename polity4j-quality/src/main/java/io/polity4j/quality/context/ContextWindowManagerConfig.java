package io.polity4j.quality.context;

import java.util.Objects;

/**
 * Configuration for the context window manager.
 *
 * maxHistoryChars — total character budget for conversation history.
 *                   When total history exceeds this, old messages
 *                   are condensed into a summary.
 *                   Default: 8000 characters.
 *
 * windowSize      — number of most recent messages to always preserve
 *                   in full, regardless of total size.
 *                   Default: 6 messages.
 *
 * summarizer      — strategy for condensing old messages.
 *                   Default: DeterministicSummarizer.
 */
public final class ContextWindowManagerConfig {

    private static final int DEFAULT_MAX_HISTORY_CHARS = 8000;
    private static final int DEFAULT_WINDOW_SIZE = 6;

    public static final ContextWindowManagerConfig DEFAULT =
            ContextWindowManagerConfig.builder().build();

    private final int maxHistoryChars;
    private final int windowSize;
    private final HistorySummarizer summarizer;

    private ContextWindowManagerConfig(Builder builder) {
        this.maxHistoryChars = builder.maxHistoryChars;
        this.windowSize = builder.windowSize;
        this.summarizer = builder.summarizer;
    }

    public int maxHistoryChars() { return maxHistoryChars; }
    public int windowSize() { return windowSize; }
    public HistorySummarizer summarizer() { return summarizer; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxHistoryChars = DEFAULT_MAX_HISTORY_CHARS;
        private int windowSize = DEFAULT_WINDOW_SIZE;
        private HistorySummarizer summarizer = new DeterministicSummarizer();

        public Builder maxHistoryChars(int maxHistoryChars) {
            if (maxHistoryChars < 1) {
                throw new IllegalArgumentException("maxHistoryChars must be positive");
            }
            this.maxHistoryChars = maxHistoryChars;
            return this;
        }

        public Builder windowSize(int windowSize) {
            if (windowSize < 1) {
                throw new IllegalArgumentException("windowSize must be at least 1");
            }
            this.windowSize = windowSize;
            return this;
        }

        public Builder summarizer(HistorySummarizer summarizer) {
            this.summarizer = Objects.requireNonNull(summarizer, "summarizer must not be null");
            return this;
        }

        public ContextWindowManagerConfig build() {
            return new ContextWindowManagerConfig(this);
        }
    }
}
