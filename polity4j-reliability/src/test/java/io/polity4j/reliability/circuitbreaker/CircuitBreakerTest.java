package io.polity4j.reliability.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    private static final CircuitBreakerConfig CONFIG = CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .cooldownDuration(Duration.ofSeconds(10))
            .successesRequiredToClose(1)
            .build();

    @Test
    void startsInClosedState() {
        var cb = new CircuitBreaker("openai", CONFIG);
        assertThat(cb.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(cb.allowCall()).isTrue();
    }

    @Test
    void testConfigValidations() {
        assertThatThrownBy(() -> CircuitBreakerConfig.builder().failureThreshold(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureThreshold must be at least 1");

        assertThatThrownBy(() -> CircuitBreakerConfig.builder().cooldownDuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cooldownDuration must not be null");

        assertThatThrownBy(() -> CircuitBreakerConfig.builder().cooldownDuration(Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cooldownDuration must not be negative");

        assertThatThrownBy(() -> CircuitBreakerConfig.builder().successesRequiredToClose(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("successesRequiredToClose must be at least 1");
    }

    @Test
    void testCircuitBreakerConstructorNullChecks() {
        assertThatThrownBy(() -> new CircuitBreaker(null, CONFIG))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("provider must not be null");

        assertThatThrownBy(() -> new CircuitBreaker("openai", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("config must not be null");

        assertThatThrownBy(() -> new CircuitBreaker("openai", CONFIG, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    void tripsOpenAfterFailureThreshold() {
        var cb = new CircuitBreaker("openai", CONFIG);

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitState.CLOSED);

        cb.recordFailure(); // threshold is 3
        assertThat(cb.state()).isEqualTo(CircuitState.OPEN);
        assertThat(cb.allowCall()).isFalse();
    }

    @Test
    void blocksCallsWhenOpen() {
        var cb = new CircuitBreaker("openai", CONFIG);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();

        assertThat(cb.allowCall()).isFalse();
    }

    @Test
    void transitionsToHalfOpenAfterCooldown() {
        Instant now = Instant.now();
        // Start with a clock at T=0
        var clock = new AdjustableClock(now);
        var cb = new CircuitBreaker("openai", CONFIG, clock);

        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitState.OPEN);

        // Advance clock past cooldown
        clock.advance(Duration.ofSeconds(11));
        assertThat(cb.allowCall()).isTrue();
        assertThat(cb.state()).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void closesAfterSuccessfulProbe() {
        Instant now = Instant.now();
        var clock = new AdjustableClock(now);
        var cb = new CircuitBreaker("openai", CONFIG, clock);

        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(11));
        cb.allowCall(); // transition to HALF_OPEN

        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(cb.allowCall()).isTrue();
    }

    @Test
    void reopensOnFailureDuringHalfOpen() {
        Instant now = Instant.now();
        var clock = new AdjustableClock(now);
        var cb = new CircuitBreaker("openai", CONFIG, clock);

        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(11));
        cb.allowCall(); // transition to HALF_OPEN

        cb.recordFailure(); // probe failed
        assertThat(cb.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void successInClosedStateResetsFailureCount() {
        var cb = new CircuitBreaker("openai", CONFIG);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.failureCount()).isEqualTo(2);

        cb.recordSuccess();
        assertThat(cb.failureCount()).isEqualTo(0);
    }

    @Test
    void doesNotTripBeforeCooldownElapses() {
        Instant now = Instant.now();
        var clock = new AdjustableClock(now);
        var cb = new CircuitBreaker("openai", CONFIG, clock);

        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();

        // Advance only 5 seconds — cooldown is 10
        clock.advance(Duration.ofSeconds(5));
        assertThat(cb.allowCall()).isFalse();
        assertThat(cb.state()).isEqualTo(CircuitState.OPEN);
    }

    // Controllable clock for deterministic time-based tests
    static class AdjustableClock extends Clock {
        private Instant now;

        AdjustableClock(Instant initial) { this.now = initial; }

        void advance(Duration duration) { this.now = now.plus(duration); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
