package io.polity4j.reliability.circuitbreaker;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe circuit breaker state machine for one provider.
 *
 * State transitions:
 *
 *   CLOSED → OPEN        when failureCount reaches failureThreshold
 *   OPEN   → HALF_OPEN   when cooldownDuration has elapsed
 *   HALF_OPEN → CLOSED   when successesRequiredToClose probe calls succeed
 *   HALF_OPEN → OPEN     when a probe call fails
 */
public final class CircuitBreaker {

    private final String provider;
    private final CircuitBreakerConfig config;
    private final Clock clock;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger probeSuccessCount = new AtomicInteger(0);
    private final AtomicReference<CircuitState> state =
            new AtomicReference<>(CircuitState.CLOSED);
    private volatile Instant openedAt = null;

    public CircuitBreaker(String provider, CircuitBreakerConfig config) {
        this(provider, config, Clock.systemUTC());
    }

    /** Package-private constructor for testing with a controllable clock */
    CircuitBreaker(String provider, CircuitBreakerConfig config, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Returns true if a call is allowed through.
     * CLOSED — always allowed.
     * OPEN — allowed only if cooldown has elapsed (transitions to HALF_OPEN).
     * HALF_OPEN — allowed for probe calls only.
     */
    public boolean allowCall() {
        return switch (state.get()) {
            case CLOSED -> true;
            case OPEN -> {
                if (cooldownElapsed()) {
                    if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                        probeSuccessCount.set(0);
                    }
                    yield true;
                }
                yield false;
            }
            case HALF_OPEN -> true;
        };
    }

    /**
     * Record a successful call.
     * In HALF_OPEN, accumulate successes — close the circuit once threshold met.
     * In CLOSED, reset the failure count.
     */
    public void recordSuccess() {
        switch (state.get()) {
            case HALF_OPEN -> {
                if (probeSuccessCount.incrementAndGet()
                        >= config.successesRequiredToClose()) {
                    state.set(CircuitState.CLOSED);
                    failureCount.set(0);
                    probeSuccessCount.set(0);
                }
            }
            case CLOSED -> failureCount.set(0);
            case OPEN -> {} // success during open shouldn't happen — ignore
        }
    }

    /**
     * Record a failed call.
     * Only provider errors count — not application errors like budget exceeded.
     * In HALF_OPEN, a single failure reopens the circuit immediately.
     */
    public void recordFailure() {
        switch (state.get()) {
            case CLOSED -> {
                if (failureCount.incrementAndGet() >= config.failureThreshold()) {
                    openedAt = clock.instant();
                    if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                        System.err.printf("WARNING: Circuit breaker for provider '%s' has tripped OPEN. Downstream calls will be blocked.%n", provider);
                    }
                }
            }
            case HALF_OPEN -> {
                openedAt = clock.instant();
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                    System.err.printf("WARNING: Circuit breaker for provider '%s' has tripped OPEN. Downstream calls will be blocked.%n", provider);
                }
            }
            case OPEN -> {} // already open — nothing to do
        }
    }

    public CircuitState state() { return state.get(); }
    public int failureCount() { return failureCount.get(); }
    public String provider() { return provider; }

    private boolean cooldownElapsed() {
        return openedAt != null && clock.instant()
                .isAfter(openedAt.plus(config.cooldownDuration()));
    }
}
