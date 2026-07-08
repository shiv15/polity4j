package io.polity4j.quality.response;

import java.util.Objects;

/**
 * Configuration settings for the response validator module.
 */
public final class ResponseValidatorConfig {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final String DEFAULT_CORRECTIVE_PROMPT =
            "The previous response failed validation. Please correct the response to align with the required criteria.";

    public static final ResponseValidatorConfig DEFAULT = builder().build();

    private final int maxRetries;
    private final String fallbackCorrectivePrompt;

    private ResponseValidatorConfig(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.fallbackCorrectivePrompt = builder.fallbackCorrectivePrompt;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public String fallbackCorrectivePrompt() {
        return fallbackCorrectivePrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String fallbackCorrectivePrompt = DEFAULT_CORRECTIVE_PROMPT;

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must not be negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder fallbackCorrectivePrompt(String fallbackCorrectivePrompt) {
            Objects.requireNonNull(fallbackCorrectivePrompt, "fallbackCorrectivePrompt must not be null");
            if (fallbackCorrectivePrompt.isBlank()) {
                throw new IllegalArgumentException("fallbackCorrectivePrompt must not be blank");
            }
            this.fallbackCorrectivePrompt = fallbackCorrectivePrompt;
            return this;
        }

        public ResponseValidatorConfig build() {
            return new ResponseValidatorConfig(this);
        }
    }
}
