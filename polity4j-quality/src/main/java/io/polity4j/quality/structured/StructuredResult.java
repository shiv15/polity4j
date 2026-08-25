package io.polity4j.quality.structured;

import io.polity4j.core.LlmResponse;

import java.util.Objects;

/**
 * Result record returned by {@link StructuredOutputPipeline}.
 *
 * @param value The deserialized target object of type T.
 * @param rawResponse The underlying LlmResponse containing latency, token counts, cost, and finish reason.
 * @param retries Count of corrective retries performed before successful deserialization.
 * @param <T> The target type.
 */
public record StructuredResult<T>(
        T value,
        LlmResponse rawResponse,
        int retries
) {
    public StructuredResult {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(rawResponse, "rawResponse must not be null");
        if (retries < 0) {
            throw new IllegalArgumentException("retries must not be negative");
        }
    }
}
