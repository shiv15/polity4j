package io.polity4j.reliability.circuitbreaker;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe circuit breaker state machine for one provider.
 * Uses a single atomic reference to an immutable snapshot record to ensure
 * atomic transitions of all state attributes as a single unit.
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

    private final AtomicReference<CircuitSnapshot> snapshot;

    private record CircuitSnapshot(
            CircuitState state,
            int failureCount,
            int probeSuccessCount,
            Instant openedAt
    ) {
        CircuitSnapshot {
            Objects.requireNonNull(state, "state must not be null");
        }
    }

    public CircuitBreaker(String provider, CircuitBreakerConfig config) {
        this(provider, config, Clock.systemUTC());
    }

    /** Package-private constructor for testing with a controllable clock */
    CircuitBreaker(String provider, CircuitBreakerConfig config, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.snapshot = new AtomicReference<>(new CircuitSnapshot(CircuitState.CLOSED, 0, 0, null));
    }

    /**
     * Returns true if a call is allowed through.
     * CLOSED — always allowed.
     * OPEN — allowed only if cooldown has elapsed (transitions to HALF_OPEN).
     * HALF_OPEN — allowed for probe calls only.
     */
    public boolean allowCall() {
        while (true) {
            CircuitSnapshot current = snapshot.get();
            switch (current.state()) {
                case CLOSED -> {
                    return true;
                }
                case OPEN -> {
                    if (cooldownElapsed(current)) {
                        CircuitSnapshot next = new CircuitSnapshot(
                                CircuitState.HALF_OPEN,
                                0,
                                0,
                                null
                        );
                        if (snapshot.compareAndSet(current, next)) {
                            return true;
                        }
                    } else {
                        return false;
                    }
                }
                case HALF_OPEN -> {
                    return true;
                }
            }
        }
    }

    /**
     * Record a successful call.
     * In HALF_OPEN, accumulate successes — close the circuit once threshold met.
     * In CLOSED, reset the failure count.
     */
    public void recordSuccess() {
        while (true) {
            CircuitSnapshot current = snapshot.get();
            switch (current.state()) {
                case HALF_OPEN -> {
                    int nextSuccessCount = current.probeSuccessCount() + 1;
                    CircuitSnapshot next;
                    if (nextSuccessCount >= config.successesRequiredToClose()) {
                        next = new CircuitSnapshot(CircuitState.CLOSED, 0, 0, null);
                    } else {
                        next = new CircuitSnapshot(CircuitState.HALF_OPEN, 0, nextSuccessCount, null);
                    }
                    if (snapshot.compareAndSet(current, next)) {
                        return;
                    }
                }
                case CLOSED -> {
                    if (current.failureCount() == 0) {
                        return;
                    }
                    CircuitSnapshot next = new CircuitSnapshot(CircuitState.CLOSED, 0, 0, null);
                    if (snapshot.compareAndSet(current, next)) {
                        return;
                    }
                }
                case OPEN -> {
                    return; // Ignore
                }
            }
        }
    }

    /**
     * Record a failed call.
     * Only provider errors count — not application errors like budget exceeded.
     * In HALF_OPEN, a single failure reopens the circuit immediately.
     */
    public void recordFailure() {
        while (true) {
            CircuitSnapshot current = snapshot.get();
            switch (current.state()) {
                case CLOSED -> {
                    int nextFailureCount = current.failureCount() + 1;
                    CircuitSnapshot next;
                    if (nextFailureCount >= config.failureThreshold()) {
                        Instant now = clock.instant();
                        next = new CircuitSnapshot(CircuitState.OPEN, nextFailureCount, 0, now);
                    } else {
                        next = new CircuitSnapshot(CircuitState.CLOSED, nextFailureCount, 0, null);
                    }
                    if (snapshot.compareAndSet(current, next)) {
                        if (next.state() == CircuitState.OPEN) {
                            System.err.printf("WARNING: Circuit breaker for provider '%s' has tripped OPEN. Downstream calls will be blocked.%n", provider);
                        }
                        return;
                    }
                }
                case HALF_OPEN -> {
                    Instant now = clock.instant();
                    CircuitSnapshot next = new CircuitSnapshot(CircuitState.OPEN, 0, 0, now);
                    if (snapshot.compareAndSet(current, next)) {
                        System.err.printf("WARNING: Circuit breaker for provider '%s' has tripped OPEN. Downstream calls will be blocked.%n", provider);
                        return;
                    }
                }
                case OPEN -> {
                    return; // Already open
                }
            }
        }
    }

    public CircuitState state() {
        return snapshot.get().state();
    }

    public int failureCount() {
        return snapshot.get().failureCount();
    }

    public String provider() {
        return provider;
    }

    private boolean cooldownElapsed(CircuitSnapshot snap) {
        return snap.openedAt() != null && clock.instant()
                .isAfter(snap.openedAt().plus(config.cooldownDuration()));
    }
}
