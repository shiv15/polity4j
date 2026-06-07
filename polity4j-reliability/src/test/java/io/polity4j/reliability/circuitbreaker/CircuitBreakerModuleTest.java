package io.polity4j.reliability.circuitbreaker;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.BudgetExceededException;
import io.polity4j.core.exception.ContextOverflowException;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;
import io.polity4j.core.exception.ResourceNotFoundException;
import io.polity4j.core.exception.PartialResponseException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerModuleTest {

    private static final LlmRequest REQUEST =
            LlmRequest.builder("Hello", "gpt-4o").build();

    private static LlmResponse okResponse() {
        return LlmResponse.builder("ok", "gpt-4o", "openai").build();
    }

    private CircuitBreakerModule moduleWithThreshold(int threshold) {
        var config = CircuitBreakerConfig.builder()
                .failureThreshold(threshold)
                .cooldownDuration(Duration.ofSeconds(30))
                .build();
        return new CircuitBreakerModule("openai", config);
    }

    @Test
    void testModuleConstructorAndProcessNullChecks() {
        var config = CircuitBreakerConfig.DEFAULT;
        assertThatThrownBy(() -> new CircuitBreakerModule((CircuitBreaker) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("circuitBreaker must not be null");

        assertThatThrownBy(() -> new CircuitBreakerModule(null, config))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("provider must not be null");

        assertThatThrownBy(() -> new CircuitBreakerModule("openai", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("config must not be null");

        var module = new CircuitBreakerModule("openai", config);
        assertThatThrownBy(() -> module.process(null, req -> okResponse()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");

        assertThatThrownBy(() -> module.process(REQUEST, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("next must not be null");
    }

    @Test
    void passesCallThroughWhenClosed() {
        var module = moduleWithThreshold(3);
        PipelineChain next = req -> okResponse();

        var response = module.process(REQUEST, next);

        assertThat(response.content()).isEqualTo("ok");
        assertThat(module.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void tripsOpenAfterThresholdProviderErrors() {
        var module = moduleWithThreshold(3);
        PipelineChain failing = req -> {
            throw new OverloadedException("openai");
        };

        for (int i = 0; i < 3; i++) {
            try { module.process(REQUEST, failing); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void blocksCallsWhenOpen() {
        var module = moduleWithThreshold(1);
        PipelineChain failing = req -> {
            throw new OverloadedException("openai");
        };

        try { module.process(REQUEST, failing); } catch (PolityException ignored) {}
        assertThat(module.state()).isEqualTo(CircuitState.OPEN);

        assertThatThrownBy(() -> module.process(REQUEST, failing))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void budgetExceptionDoesNotCountAsProviderFailure() {
        var module = moduleWithThreshold(2);
        PipelineChain budgetFailing = req -> {
            throw new BudgetExceededException(
                    new BigDecimal("10.00"), new BigDecimal("12.00"));
        };

        // Fire budget exception many times — circuit should stay closed
        for (int i = 0; i < 5; i++) {
            try { module.process(REQUEST, budgetFailing); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void contextOverflowDoesNotCountAsProviderFailure() {
        var module = moduleWithThreshold(2);
        PipelineChain contextFailing = req -> {
            throw new ContextOverflowException(1000, 500);
        };

        for (int i = 0; i < 5; i++) {
            try { module.process(REQUEST, contextFailing); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void resourceNotFoundDoesNotCountAsProviderFailure() {
        var module = moduleWithThreshold(2);
        PipelineChain resourceFailing = req -> {
            throw new ResourceNotFoundException("Model model not found at provider openai");
        };

        for (int i = 0; i < 5; i++) {
            try { module.process(REQUEST, resourceFailing); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void rateLimitCountsAsProviderFailure() {
        var module = moduleWithThreshold(2);
        PipelineChain rateLimited = req -> {
            throw new RateLimitException("openai", 1000L);
        };

        for (int i = 0; i < 2; i++) {
            try { module.process(REQUEST, rateLimited); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void partialResponseCountsAsProviderFailure() {
        var module = moduleWithThreshold(2);
        PipelineChain partialFailing = req -> {
            throw new PartialResponseException("Model failed to respond completely");
        };

        for (int i = 0; i < 2; i++) {
            try { module.process(REQUEST, partialFailing); } catch (PolityException ignored) {}
        }

        assertThat(module.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void nameIncludesProvider() {
        var module = moduleWithThreshold(3);
        assertThat(module.name()).isEqualTo("circuit-breaker:openai");
    }
}
