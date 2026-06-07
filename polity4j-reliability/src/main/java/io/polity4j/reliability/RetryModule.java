package io.polity4j.reliability;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;

import java.time.Duration;
import java.util.Objects;

/**
 * Retries failed AI calls with exponential backoff.
 *
 * Handles LLM-specific failure modes differently:
 *
 *   RateLimitException  — retryable by default. If the provider sent a
 *                         retry-after value, that overrides calculated backoff.
 *
 *   OverloadedException — NOT retried by default. Provider is saturated;
 *                         retrying the same provider wastes time. The fallback
 *                         module should handle this by routing elsewhere.
 *
 *   ModelUnavailableException — never retried. Provider is down; the circuit
 *                               breaker and fallback chain handle this.
 *
 * Any other PolityException — not retried, propagated immediately.
 */
public final class RetryModule implements PipelineModule {

    private final RetryConfig config;
    private final Sleeper sleeper;

    public RetryModule() {
        this(RetryConfig.DEFAULT);
    }

    public RetryModule(int maxAttempts) {
        this(RetryConfig.builder().maxAttempts(maxAttempts).build());
    }

    public RetryModule(RetryConfig config) {
        this(Objects.requireNonNull(config, "config must not be null"), Thread::sleep);
    }

    /**
     * Package-private constructor for testing — accepts a Sleeper so
     * tests don't actually wait for real delays.
     */
    RetryModule(RetryConfig config, Sleeper sleeper) {
        this.config = java.util.Objects.requireNonNull(config, "config must not be null");
        this.sleeper = java.util.Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    RetryModule(int maxAttempts, Sleeper sleeper) {
        this(RetryConfig.builder().maxAttempts(maxAttempts).build(), sleeper);
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");
        PolityException lastException = null;

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                return next.proceed(request);
            } catch (RateLimitException e) {
                if (!config.retryOnRateLimit() || attempt == config.maxAttempts()) {
                    throw e;
                }
                lastException = e;
                // Provider told us how long to wait — respect it
                sleep(e.retryAfterMs() > 0
                        ? Duration.ofMillis(e.retryAfterMs())
                        : calculateBackoff(attempt));

            } catch (OverloadedException e) {
                if (!config.retryOnOverloaded() || attempt == config.maxAttempts()) {
                    throw e;
                }
                lastException = e;
                sleep(calculateBackoff(attempt));

            } catch (ModelUnavailableException e) {
                // Never retry — provider is down, not just busy
                throw e;

            } catch (PolityException e) {
                // Any other PolityException — not retryable
                throw e;
            }
        }

        // Should never reach here — loop always returns or throws
        throw lastException;
    }

    @Override
    public String name() { return "retry"; }

    private Duration calculateBackoff(int attempt) {
        // attempt 1 → initialDelay * multiplier^0 = initialDelay
        // attempt 2 → initialDelay * multiplier^1
        // attempt 3 → initialDelay * multiplier^2
        double delayMs = config.initialDelay().toMillis()
                * Math.pow(config.multiplier(), attempt - 1);
        long cappedMs = Math.min((long) delayMs, config.maxDelay().toMillis());
        return Duration.ofMillis(cappedMs);
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", e);
        }
    }

    /**
     * Abstraction over Thread.sleep so tests can inject a no-op.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }
}
