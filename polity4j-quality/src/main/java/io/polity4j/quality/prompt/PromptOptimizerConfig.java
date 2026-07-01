package io.polity4j.quality.prompt;

/**
 * Configuration for the prompt optimizer.
 *
 * All optimizations are opt-in — none are applied unless explicitly
 * enabled. This makes the optimizer's effect on requests predictable
 * and auditable.
 */
public final class PromptOptimizerConfig {

    public static final PromptOptimizerConfig DEFAULT =
            PromptOptimizerConfig.builder()
                    .normalizeWhitespace(true)
                    .deduplicateHistory(true)
                    .build();

    private final boolean normalizeWhitespace;
    private final boolean deduplicateHistory;
    private final int maxPromptChars;

    private PromptOptimizerConfig(Builder builder) {
        this.normalizeWhitespace = builder.normalizeWhitespace;
        this.deduplicateHistory = builder.deduplicateHistory;
        this.maxPromptChars = builder.maxPromptChars;
    }

    public boolean normalizeWhitespace() { return normalizeWhitespace; }
    public boolean deduplicateHistory() { return deduplicateHistory; }

    /**
     * Maximum total characters allowed in the prompt before history
     * truncation is applied. 0 means no limit enforced.
     */
    public int maxPromptChars() { return maxPromptChars; }
    public boolean hasMaxPromptChars() { return maxPromptChars > 0; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean normalizeWhitespace = false;
        private boolean deduplicateHistory = false;
        private int maxPromptChars = 0;

        public Builder normalizeWhitespace(boolean normalizeWhitespace) {
            this.normalizeWhitespace = normalizeWhitespace;
            return this;
        }

        public Builder deduplicateHistory(boolean deduplicateHistory) {
            this.deduplicateHistory = deduplicateHistory;
            return this;
        }

        public Builder maxPromptChars(int maxPromptChars) {
            if (maxPromptChars < 0) throw new IllegalArgumentException(
                    "maxPromptChars must not be negative");
            this.maxPromptChars = maxPromptChars;
            return this;
        }

        public PromptOptimizerConfig build() {
            return new PromptOptimizerConfig(this);
        }
    }
}
