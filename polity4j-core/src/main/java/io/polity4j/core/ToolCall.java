package io.polity4j.core;

import java.util.Map;
import java.util.Objects;

/**
 * Representation of a tool call requested by an LLM in its response.
 *
 * @param id the unique tool call identifier assigned by provider
 * @param name the tool function name requested by the model
 * @param arguments the parsed key-value argument map supplied by the model
 */
public record ToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {

    public ToolCall {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
    }
}
