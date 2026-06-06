package io.polity4j.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing everything needed to make one AI call.
 * This is the input to every pipeline module and every adapter.
 *
 * All fields except prompt and model are optional — use the builder.
 */
public record LlmRequest(
        String prompt,
        String model,
        int maxTokens,
        String callerId,
        String regionContext,
        List<Message> conversationHistory
) {

    public record Message(String role, String content) {
        public Message {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    public LlmRequest {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(model, "model must not be null");
        if (prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        if (model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        if (maxTokens < 0) throw new IllegalArgumentException("maxTokens must not be negative");
        conversationHistory = conversationHistory == null
                ? List.of()
                : List.copyOf(conversationHistory);
    }

    public static Builder builder(String prompt, String model) {
        return new Builder(prompt, model);
    }

    public static final class Builder {
        private final String prompt;
        private final String model;
        private int maxTokens = 1024;
        private String callerId;
        private String regionContext;
        private List<Message> conversationHistory;

        private Builder(String prompt, String model) {
            this.prompt = prompt;
            this.model = model;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder callerId(String callerId) {
            this.callerId = callerId;
            return this;
        }

        public Builder regionContext(String regionContext) {
            this.regionContext = regionContext;
            return this;
        }

        public Builder conversationHistory(List<Message> conversationHistory) {
            this.conversationHistory = conversationHistory;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(
                    prompt, model, maxTokens,
                    callerId, regionContext, conversationHistory);
        }
    }
}
