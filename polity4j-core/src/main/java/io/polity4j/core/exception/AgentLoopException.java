package io.polity4j.core.exception;

/**
 * Exception thrown when an recursive agent loop is detected.
 * Uses HTTP status code 422 (Unprocessable Entity).
 */
public final class AgentLoopException extends PolityException {

    public AgentLoopException(String message) {
        super(message, 422);
    }

    public AgentLoopException(String message, Throwable cause) {
        super(message, 422, cause);
    }
}
