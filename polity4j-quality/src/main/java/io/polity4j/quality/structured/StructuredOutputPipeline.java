package io.polity4j.quality.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ResponseValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Type-safe pipeline wrapper enforcing JSON output schemas and auto-corrective retries.
 *
 * @param <T> The target Java POJO or Record type.
 */
public final class StructuredOutputPipeline<T> {

    public static final int DEFAULT_MAX_RETRIES = 3;

    private final LlmPipeline pipeline;
    private final Class<T> targetType;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    private StructuredOutputPipeline(LlmPipeline pipeline, Class<T> targetType, ObjectMapper objectMapper, int maxRetries) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        this.maxRetries = maxRetries;
    }

    public static <T> StructuredOutputPipeline<T> of(LlmPipeline pipeline, Class<T> targetType) {
        return new StructuredOutputPipeline<>(pipeline, targetType, null, DEFAULT_MAX_RETRIES);
    }

    public static <T> StructuredOutputPipeline<T> of(LlmPipeline pipeline, Class<T> targetType, ObjectMapper objectMapper, int maxRetries) {
        return new StructuredOutputPipeline<>(pipeline, targetType, objectMapper, maxRetries);
    }

    public StructuredResult<T> execute(LlmRequest request) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");

        String jsonInstruction = "Respond ONLY with a valid JSON object matching the structure of type '"
                + targetType.getSimpleName()
                + "'. Do not wrap in markdown or output any explanation outside the raw JSON.";

        String updatedSystemPrompt = request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? jsonInstruction
                : request.systemPrompt() + "\n" + jsonInstruction;

        LlmRequest currentRequest = LlmRequest.builder(request.prompt(), request.model())
                .maxTokens(request.maxTokens())
                .callerId(request.callerId())
                .regionContext(request.regionContext())
                .conversationHistory(request.conversationHistory())
                .temperature(request.temperature())
                .topP(request.topP())
                .frequencyPenalty(request.frequencyPenalty())
                .presencePenalty(request.presencePenalty())
                .additionalParams(request.additionalParams())
                .systemPrompt(updatedSystemPrompt)
                .tools(request.tools())
                .build();

        int attempt = 0;
        List<LlmRequest.Message> history = new ArrayList<>(currentRequest.conversationHistory());

        while (attempt <= maxRetries) {
            LlmResponse response = pipeline.execute(currentRequest);
            String rawText = response.content();
            String cleanedJson = stripMarkdownCodeFences(rawText);

            try {
                T deserialized = objectMapper.readValue(cleanedJson, targetType);
                return new StructuredResult<>(deserialized, response, attempt);
            } catch (JsonProcessingException e) {
                if (attempt == maxRetries) {
                    throw new ResponseValidationException(
                            "Failed to deserialize response into " + targetType.getName() + " after " + (maxRetries + 1) + " attempts: " + e.getOriginalMessage(),
                            cleanedJson,
                            e
                    );
                }

                attempt++;
                history.add(new LlmRequest.Message("assistant", rawText));
                history.add(new LlmRequest.Message("user", "Your previous response could not be parsed as valid JSON for type '"
                        + targetType.getSimpleName() + "'. Error: " + e.getOriginalMessage() + ". Please fix syntax and respond ONLY with valid JSON."));

                currentRequest = LlmRequest.builder(request.prompt(), request.model())
                        .maxTokens(request.maxTokens())
                        .callerId(request.callerId())
                        .regionContext(request.regionContext())
                        .conversationHistory(history)
                        .temperature(request.temperature())
                        .topP(request.topP())
                        .frequencyPenalty(request.frequencyPenalty())
                        .presencePenalty(request.presencePenalty())
                        .additionalParams(request.additionalParams())
                        .systemPrompt(updatedSystemPrompt)
                        .tools(request.tools())
                        .build();
            }
        }

        throw new ResponseValidationException("Max retries exceeded for structured output", "");
    }

    private static String stripMarkdownCodeFences(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
