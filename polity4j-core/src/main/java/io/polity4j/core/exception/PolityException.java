package io.polity4j.core.exception;

import java.time.Instant;

/**
 * Base custom sealed runtime exception for the Polity library.
 * Defines standard status code and timestamp tracking.
 */
public abstract sealed class PolityException extends RuntimeException permits ResourceNotFoundException, RateLimitException, OverloadedException, ContextOverflowException, PartialResponseException, BudgetExceededException, ModelUnavailableException, AgentLoopException {
    private final int statusCode;
    private final Instant timestamp;

    protected PolityException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.timestamp = Instant.now();
    }

    protected PolityException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.timestamp = Instant.now();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
