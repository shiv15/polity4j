package io.polity4j.reliability.circuitbreaker;

/**
 * The three states of a circuit breaker.
 *
 * CLOSED   → calls pass through normally
 * OPEN     → calls are blocked, ModelUnavailableException thrown immediately
 * HALF_OPEN → one probe call allowed through to test provider recovery
 */
public enum CircuitState {
    CLOSED, OPEN, HALF_OPEN
}
