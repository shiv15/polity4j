package io.polity4j.core.exception;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Thrown when the agent loop detector trips.
 *
 * Carries the reason it tripped and the session state at the time
 * so callers can log, alert, or decide what to do with the partial
 * result.
 *
 * This is NOT a provider failure — the provider is fine.
 * CircuitBreakerModule correctly ignores this exception.
 */
public final class AgentLoopException extends PolityException {

    public enum TripReason {
        MAX_ITERATIONS_EXCEEDED,
        MAX_COST_EXCEEDED,
        STAGNATION_DETECTED,
        FREQUENCY_LIMIT_EXCEEDED
    }

    private final TripReason tripReason;
    private final int iterations;
    private final BigDecimal accumulatedCost;

    public AgentLoopException(TripReason tripReason,
                              int iterations,
                              BigDecimal accumulatedCost) {
        this("Agent loop detected — reason: " + tripReason
                + ", iterations: " + iterations
                + ", accumulated cost: $" + accumulatedCost,
             tripReason, iterations, accumulatedCost);
    }

    public AgentLoopException(String message,
                              TripReason tripReason,
                              int iterations,
                              BigDecimal accumulatedCost) {
        super(message, 422);
        this.tripReason = Objects.requireNonNull(tripReason, "tripReason must not be null");
        this.iterations = iterations;
        this.accumulatedCost = Objects.requireNonNull(accumulatedCost, "accumulatedCost must not be null");
    }

    public TripReason tripReason() { return tripReason; }
    public int iterations() { return iterations; }
    public BigDecimal accumulatedCost() { return accumulatedCost; }
}
