package io.polity4j.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object representing the result of one AI call.
 * This is what every adapter produces and every pipeline module receives
 * on the response side.
 */
public record LlmResponse(
        String content,
        String model,
        String provider,
        int inputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        long latencyMs
) {

    public LlmResponse {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        if (inputTokens < 0) throw new IllegalArgumentException("inputTokens must not be negative");
        if (outputTokens < 0) throw new IllegalArgumentException("outputTokens must not be negative");
        if (latencyMs < 0) throw new IllegalArgumentException("latencyMs must not be negative");
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public static Builder builder(String content, String model, String provider) {
        return new Builder(content, model, provider);
    }

    public static final class Builder {
        private final String content;
        private final String model;
        private final String provider;
        private int inputTokens;
        private int outputTokens;
        private BigDecimal estimatedCost = BigDecimal.ZERO;
        private long latencyMs;

        private Builder(String content, String model, String provider) {
            this.content = content;
            this.model = model;
            this.provider = provider;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder estimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public LlmResponse build() {
            return new LlmResponse(
                    content, model, provider,
                    inputTokens, outputTokens,
                    estimatedCost, latencyMs);
        }
    }
}
