package io.polity4j.reliability;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the retry module.
 *
 * Backoff works as follows:
 *   attempt 1 fails → wait initialDelay
 *   attempt 2 fails → wait initialDelay * multiplier
 *   attempt 3 fails → wait initialDelay * multiplier^2
 *   ... up to maxDelay cap
 *
 * RateLimitException is special — if the provider sends a retry-after
 * value, that overrides the calculated backoff entirely.
 */
public final class RetryConfig {

    public static final RetryConfig DEFAULT = RetryConfig.builder().build();

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final boolean retryOnRateLimit;
    private final boolean retryOnOverloaded;

    private RetryConfig(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.multiplier = builder.multiplier;
        this.retryOnRateLimit = builder.retryOnRateLimit;
        this.retryOnOverloaded = builder.retryOnOverloaded;
    }

    public int maxAttempts() { return maxAttempts; }
    public Duration initialDelay() { return initialDelay; }
    public Duration maxDelay() { return maxDelay; }
    public double multiplier() { return multiplier; }
    public boolean retryOnRateLimit() { return retryOnRateLimit; }
    public boolean retryOnOverloaded() { return retryOnOverloaded; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(500);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private boolean retryOnRateLimit = true;
        private boolean retryOnOverloaded = false;

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) throw new IllegalArgumentException(
                "maxAttempts must be at least 1");
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            Objects.requireNonNull(initialDelay, "initialDelay must not be null");
            if (initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay must not be negative");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            Objects.requireNonNull(maxDelay, "maxDelay must not be null");
            if (maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay must not be negative");
            }
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be a finite number at least 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        public Builder retryOnRateLimit(boolean retryOnRateLimit) {
            this.retryOnRateLimit = retryOnRateLimit;
            return this;
        }

        public Builder retryOnOverloaded(boolean retryOnOverloaded) {
            this.retryOnOverloaded = retryOnOverloaded;
            return this;
        }

        public RetryConfig build() { return new RetryConfig(this); }
    }
}
