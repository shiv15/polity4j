package io.polity4j.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<Message> conversationHistory,
        Double temperature,
        Double topP,
        Double frequencyPenalty,
        Double presencePenalty,
        Map<String, Object> additionalParams,
        String systemPrompt
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
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (topP != null && (topP < 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0");
        }
        if (frequencyPenalty != null && (frequencyPenalty < -2.0 || frequencyPenalty > 2.0)) {
            throw new IllegalArgumentException("frequencyPenalty must be between -2.0 and 2.0");
        }
        if (presencePenalty != null && (presencePenalty < -2.0 || presencePenalty > 2.0)) {
            throw new IllegalArgumentException("presencePenalty must be between -2.0 and 2.0");
        }
        conversationHistory = conversationHistory == null
                ? List.of()
                : List.copyOf(conversationHistory);
        additionalParams = additionalParams == null
                ? Map.of()
                : Map.copyOf(additionalParams);
    }

    public LlmRequest(String prompt, String model, int maxTokens, String callerId, String regionContext, List<Message> conversationHistory) {
        this(prompt, model, maxTokens, callerId, regionContext, conversationHistory, null, null, null, null, Map.of(), null);
    }

    public LlmRequest(String prompt, String model, int maxTokens, String callerId, String regionContext, List<Message> conversationHistory, Double temperature, Double topP, Double frequencyPenalty, Double presencePenalty, Map<String, Object> additionalParams) {
        this(prompt, model, maxTokens, callerId, regionContext, conversationHistory, temperature, topP, frequencyPenalty, presencePenalty, additionalParams, null);
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
        private Double temperature;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Map<String, Object> additionalParams = new HashMap<>();
        private String systemPrompt;

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

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder additionalParams(Map<String, Object> additionalParams) {
            if (additionalParams != null) {
                this.additionalParams.putAll(additionalParams);
            }
            return this;
        }

        public Builder additionalParam(String key, Object value) {
            Objects.requireNonNull(key, "key must not be null");
            this.additionalParams.put(key, value);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(
                    prompt, model, maxTokens,
                    callerId, regionContext, conversationHistory,
                    temperature, topP, frequencyPenalty, presencePenalty,
                    additionalParams, systemPrompt);
        }
    }
}

