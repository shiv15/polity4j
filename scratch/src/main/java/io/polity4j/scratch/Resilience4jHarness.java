package io.polity4j.scratch;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Native harness for Resilience4j.
 * Configured with normalized backoff parameters:
 * maxAttempts=3, initialDelay=100ms, multiplier=2.0, maxDelay=2000ms,
 * and retryOnException set to match ANY thrown exception per specification.
 */
public final class Resilience4jHarness {

    private final Retry retry;

    public Resilience4jHarness() {
        IntervalFunction intervalFn = IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(2000)
        );

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(intervalFn)
                .retryOnException(e -> true) // matches ANY thrown exception
                .build();

        this.retry = Retry.of("resilience4jHarness", config);
    }

    public <T> T execute(Supplier<T> supplier) {
        Supplier<T> decorated = Retry.decorateSupplier(retry, supplier);
        return decorated.get();
    }
}
