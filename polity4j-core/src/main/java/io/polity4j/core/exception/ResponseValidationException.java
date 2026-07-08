package io.polity4j.core.exception;

import java.util.Objects;

/**
 * Thrown when an LLM response fails validation after retries are exhausted.
 * This class inherits from the sealed base class PolityException.
 */
public final class ResponseValidationException extends PolityException {

    private final String invalidResponseContent;

    public ResponseValidationException(String message, String invalidResponseContent) {
        super(message, 422);
        this.invalidResponseContent = Objects.requireNonNull(invalidResponseContent, "invalidResponseContent must not be null");
    }

    public ResponseValidationException(String message, String invalidResponseContent, Throwable cause) {
        super(message, 422, cause);
        this.invalidResponseContent = Objects.requireNonNull(invalidResponseContent, "invalidResponseContent must not be null");
    }

    public String invalidResponseContent() {
        return invalidResponseContent;
    }
}
