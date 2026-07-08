package io.polity4j.quality.response;

import io.polity4j.core.LlmResponse;

/**
 * Functional interface for validating the contents of an LLM response.
 */
@FunctionalInterface
public interface ResponseValidator {

    /**
     * Validates the given LLM response.
     *
     * @param response the response to validate
     * @return a ValidationResult indicating success or failure
     */
    ValidationResult validate(LlmResponse response);
}
