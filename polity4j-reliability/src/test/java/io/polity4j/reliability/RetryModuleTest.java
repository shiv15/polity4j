package io.polity4j.reliability;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.ContextOverflowException;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryModuleTest {

    private static final LlmRequest REQUEST =
            LlmRequest.builder("Hello", "gpt-4o").build();

    // No-op sleeper — tests run instantly
    private static final RetryModule.Sleeper NO_SLEEP = ms -> {};

    private static LlmResponse okResponse() {
        return LlmResponse.builder("ok", "gpt-4o", "openai").build();
    }

    // ------------------------------------------------------------------
    // Helpers to build chains that fail N times then succeed
    // ------------------------------------------------------------------

    private PipelineChain failThenSucceed(int failures, PolityException error) {
        int[] attempts = {0};
        return request -> {
            attempts[0]++;
            if (attempts[0] <= failures) throw error;
            return okResponse();
        };
    }

    private PipelineChain alwaysFail(PolityException error) {
        return request -> { throw error; };
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void testConstructorsAndDefaultValues() {
        // Test default constructor
        var defaultModule = new RetryModule();
        assertThat(defaultModule.name()).isEqualTo("retry");

        // Test constructor with int maxAttempts
        var countModule = new RetryModule(5);
        assertThat(countModule.name()).isEqualTo("retry");

        // Test constructor with config
        var config = RetryConfig.builder().maxAttempts(4).build();
        var configModule = new RetryModule(config);
        assertThat(configModule.name()).isEqualTo("retry");

        // Test null validations
        assertThatThrownBy(() -> new RetryModule((RetryConfig) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("config must not be null");
        assertThatThrownBy(() -> new RetryModule(null, NO_SLEEP))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("config must not be null");
        assertThatThrownBy(() -> new RetryModule(config, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sleeper must not be null");
    }

    @Test
    void testRetryConfigBuilderValidation() {
        assertThatThrownBy(() -> RetryConfig.builder().maxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryConfig.builder().multiplier(0.9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryConfig.builder().multiplier(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("multiplier must be a finite number at least 1.0");
        assertThatThrownBy(() -> RetryConfig.builder().multiplier(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("multiplier must be a finite number at least 1.0");
        assertThatThrownBy(() -> RetryConfig.builder().multiplier(Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("multiplier must be a finite number at least 1.0");
        assertThatThrownBy(() -> RetryConfig.builder().initialDelay(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialDelay must not be null");
        assertThatThrownBy(() -> RetryConfig.builder().maxDelay(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maxDelay must not be null");
        assertThatThrownBy(() -> RetryConfig.builder().initialDelay(Duration.ofMillis(-100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be negative");
        assertThatThrownBy(() -> RetryConfig.builder().maxDelay(Duration.ofMillis(-5000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDelay must not be negative");
    }

    @Test
    void succeedsFirstAttemptWithNoRetry() {
        var module = new RetryModule(RetryConfig.DEFAULT, NO_SLEEP);
        var chain = (PipelineChain) request -> okResponse();

        var response = module.process(REQUEST, chain);

        assertThat(response.content()).isEqualTo("ok");
    }

    @Test
    void retriesOnRateLimitAndEventuallySucceeds() {
        var module = new RetryModule(RetryConfig.DEFAULT, NO_SLEEP);
        // Fail twice with rate limit, succeed on third attempt
        var chain = failThenSucceed(2, new RateLimitException("openai", 0));

        var response = module.process(REQUEST, chain);

        assertThat(response.content()).isEqualTo("ok");
    }

    @Test
    void throwsAfterExhaustingAllAttempts() {
        var config = RetryConfig.builder().maxAttempts(3).build();
        var module = new RetryModule(config, NO_SLEEP);
        var chain = alwaysFail(new RateLimitException("openai", 0));

        assertThatThrownBy(() -> module.process(REQUEST, chain))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void respectsProviderRetryAfterValue() {
        var sleepLog = new ArrayList<Long>();
        RetryModule.Sleeper recordingSleeper = ms -> sleepLog.add(ms);

        var config = RetryConfig.builder().maxAttempts(2).build();
        var module = new RetryModule(config, recordingSleeper);
        // Provider says wait 3000ms
        var chain = failThenSucceed(1, new RateLimitException("openai", 3000L));

        module.process(REQUEST, chain);

        assertThat(sleepLog).containsExactly(3000L);
    }

    @Test
    void usesCalculatedBackoffWhenNoRetryAfterValue() {
        var sleepLog = new ArrayList<Long>();
        RetryModule.Sleeper recordingSleeper = ms -> sleepLog.add(ms);

        var config = RetryConfig.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(500))
                .build();
        var module = new RetryModule(config, recordingSleeper);
        // retryAfterMs = 0 means no provider hint — use calculated backoff
        var chain = failThenSucceed(1, new RateLimitException("openai", 0));

        module.process(REQUEST, chain);

        assertThat(sleepLog).containsExactly(500L);
    }

    @Test
    void doesNotRetryOverloadedByDefault() {
        var module = new RetryModule(RetryConfig.DEFAULT, NO_SLEEP);
        var chain = alwaysFail(new OverloadedException("anthropic"));

        assertThatThrownBy(() -> module.process(REQUEST, chain))
                .isInstanceOf(OverloadedException.class);
    }

    @Test
    void retriesOverloadedWhenConfigured() {
        var config = RetryConfig.builder()
                .maxAttempts(3)
                .retryOnOverloaded(true)
                .build();
        var module = new RetryModule(config, NO_SLEEP);
        var chain = failThenSucceed(2, new OverloadedException("anthropic"));

        var response = module.process(REQUEST, chain);

        assertThat(response.content()).isEqualTo("ok");
    }

    @Test
    void neverRetriesModelUnavailable() {
        var config = RetryConfig.builder().maxAttempts(5).build();
        var module = new RetryModule(config, NO_SLEEP);
        // Only fails once — but should throw immediately, not retry
        var chain = alwaysFail(new ModelUnavailableException("gpt-4o", "openai"));

        assertThatThrownBy(() -> module.process(REQUEST, chain))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void doesNotRetryNonRetryableExceptions() {
        var config = RetryConfig.builder().maxAttempts(5).build();
        var module = new RetryModule(config, NO_SLEEP);
        var chain = alwaysFail(new ContextOverflowException(200_000, 128_000));

        assertThatThrownBy(() -> module.process(REQUEST, chain))
                .isInstanceOf(ContextOverflowException.class);
    }

    @Test
    void backoffIsExponential() {
        var sleepLog = new ArrayList<Long>();
        RetryModule.Sleeper recordingSleeper = ms -> sleepLog.add(ms);

        var config = RetryConfig.builder()
                .maxAttempts(4)
                .initialDelay(Duration.ofMillis(100))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(30))
                .build();
        var module = new RetryModule(config, recordingSleeper);
        var chain = failThenSucceed(3, new RateLimitException("openai", 0));

        module.process(REQUEST, chain);

        // attempt 1 fails → 100ms
        // attempt 2 fails → 200ms
        // attempt 3 fails → 400ms
        // attempt 4 succeeds → no sleep
        assertThat(sleepLog).containsExactly(100L, 200L, 400L);
    }

    @Test
    void backoffIsCappedAtMaxDelay() {
        var sleepLog = new ArrayList<Long>();
        RetryModule.Sleeper recordingSleeper = ms -> sleepLog.add(ms);

        var config = RetryConfig.builder()
                .maxAttempts(4)
                .initialDelay(Duration.ofMillis(1000))
                .multiplier(10.0)
                .maxDelay(Duration.ofMillis(5000))
                .build();
        var module = new RetryModule(config, recordingSleeper);
        var chain = failThenSucceed(3, new RateLimitException("openai", 0));

        module.process(REQUEST, chain);

        // Without cap: 1000, 10000, 100000
        // With 5000ms cap: 1000, 5000, 5000
        assertThat(sleepLog).containsExactly(1000L, 5000L, 5000L);
    }

    @Test
    void recordsAttemptCount() {
        var attempts = new ArrayList<Integer>();
        int[] count = {0};
        PipelineChain countingChain = request -> {
            count[0]++;
            attempts.add(count[0]);
            if (count[0] < 3) throw new RateLimitException("openai", 0);
            return okResponse();
        };

        var config = RetryConfig.builder().maxAttempts(3).build();
        var module = new RetryModule(config, NO_SLEEP);
        module.process(REQUEST, countingChain);

        assertThat(attempts).hasSize(3);
    }

    @Test
    void testInterruptedSleepPropagates() {
        RetryModule.Sleeper interruptedSleeper = ms -> {
            throw new InterruptedException("Simulated interruption");
        };
        var module = new RetryModule(2, interruptedSleeper);
        var chain = failThenSucceed(1, new RateLimitException("openai", 0));

        assertThatThrownBy(() -> module.process(REQUEST, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Retry sleep interrupted");
    }

    @Test
    void testProcessNullValidation() {
        var module = new RetryModule();
        assertThatThrownBy(() -> module.process(null, request -> okResponse()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        assertThatThrownBy(() -> module.process(REQUEST, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("next must not be null");
    }
}
