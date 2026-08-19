package io.polity4j.core;

import java.util.Map;
import java.util.Objects;

/**
 * Representation of a tool specification (function definition)
 * passed to an LLM provider.
 *
 * @param name the tool function name
 * @param description a description of what the tool function does
 * @param parameters JSON Schema object defining required parameters and types
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, Object> parameters
) {

    public ToolSpec {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        description = description != null ? description : "";
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of("type", "object", "properties", Map.of());
    }

    public static ToolSpec of(String name, String description, Map<String, Object> parameters) {
        return new ToolSpec(name, description, parameters);
    }
}
