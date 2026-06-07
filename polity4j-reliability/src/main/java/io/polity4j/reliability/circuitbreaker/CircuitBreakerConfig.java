package io.polity4j.reliability.circuitbreaker;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the circuit breaker.
 *
 * The circuit breaker has three states:
 *
 *   CLOSED   — normal operation, calls pass through
 *   OPEN     — provider is failing, calls are blocked immediately
 *   HALF_OPEN — cooldown elapsed, one probe call allowed through
 *               to test if provider has recovered
 */
public final class CircuitBreakerConfig {

    public static final CircuitBreakerConfig DEFAULT =
            CircuitBreakerConfig.builder().build();

    private final int failureThreshold;
    private final Duration cooldownDuration;
    private final int successesRequiredToClose;

    private CircuitBreakerConfig(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.cooldownDuration = builder.cooldownDuration;
        this.successesRequiredToClose = builder.successesRequiredToClose;
    }

    public int failureThreshold() { return failureThreshold; }
    public Duration cooldownDuration() { return cooldownDuration; }
    public int successesRequiredToClose() { return successesRequiredToClose; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int failureThreshold = 5;
        private Duration cooldownDuration = Duration.ofSeconds(30);
        private int successesRequiredToClose = 1;

        public Builder failureThreshold(int failureThreshold) {
            if (failureThreshold < 1) throw new IllegalArgumentException(
                    "failureThreshold must be at least 1");
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder cooldownDuration(Duration cooldownDuration) {
            Objects.requireNonNull(cooldownDuration, "cooldownDuration must not be null");
            if (cooldownDuration.isNegative()) {
                throw new IllegalArgumentException("cooldownDuration must not be negative");
            }
            this.cooldownDuration = cooldownDuration;
            return this;
        }

        public Builder successesRequiredToClose(int successesRequiredToClose) {
            if (successesRequiredToClose < 1) throw new IllegalArgumentException(
                    "successesRequiredToClose must be at least 1");
            this.successesRequiredToClose = successesRequiredToClose;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
