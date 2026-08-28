package io.polity4j.scratch;

/**
 * Semantic fault vocabulary for fault-injection benchmark harness.
 */
public enum FaultType {
    RATE_LIMITED,
    OVERLOADED,
    TRANSIENT_5XX,
    PERMANENT_4XX,
    MALFORMED_RESPONSE,
    SUCCESS
}
