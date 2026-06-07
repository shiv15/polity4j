package io.polity4j.reliability.fallback;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.BudgetExceededException;
import io.polity4j.core.exception.ContextOverflowException;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackChainModuleTest {

    private static final LlmRequest REQUEST =
            LlmRequest.builder("Hello", "gpt-4o").build();

    private static LlmResponse okResponse(String provider) {
        return LlmResponse.builder("ok", "gpt-4o", provider).build();
    }

    private static LlmClient successClient(String provider) {
        return new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                return okResponse(provider);
            }
            @Override
            public String provider() { return provider; }
        };
    }

    private static LlmClient failingClient(String provider, PolityException error) {
        return new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) { throw error; }
            @Override
            public String provider() { return provider; }
        };
    }

    private static PipelineChain succeedingChain(String provider) {
        return request -> okResponse(provider);
    }

    private static PipelineChain failingChain(PolityException error) {
        return request -> { throw error; };
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void primarySuccessSkipsFallbacks() {
        var module = new FallbackChainModule(List.of(successClient("anthropic")));

        var response = module.process(REQUEST, succeedingChain("openai"));

        assertThat(response.provider()).isEqualTo("openai");
    }

    @Test
    void usesFirstFallbackWhenPrimaryFails() {
        var module = new FallbackChainModule(List.of(
                successClient("anthropic"),
                successClient("ollama")));

        var response = module.process(REQUEST,
                failingChain(new ModelUnavailableException("gpt-4o", "openai")));

        assertThat(response.provider()).isEqualTo("anthropic");
    }

    @Test
    void usesSecondFallbackWhenFirstFallbackAlsoFails() {
        var module = new FallbackChainModule(List.of(
                failingClient("anthropic", new OverloadedException("anthropic")),
                successClient("ollama")));

        var response = module.process(REQUEST,
                failingChain(new ModelUnavailableException("gpt-4o", "openai")));

        assertThat(response.provider()).isEqualTo("ollama");
    }

    @Test
    void throwsModelUnavailableWhenAllFallbacksExhausted() {
        var module = new FallbackChainModule(List.of(
                failingClient("anthropic", new OverloadedException("anthropic")),
                failingClient("ollama",
                        new ModelUnavailableException("llama3", "ollama"))));

        assertThatThrownBy(() -> module.process(REQUEST,
                failingChain(new ModelUnavailableException("gpt-4o", "openai"))))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("all-providers-exhausted");
    }

    @Test
    void doesNotFallbackOnBudgetException() {
        var module = new FallbackChainModule(List.of(successClient("anthropic")));

        PipelineChain budgetFailing = request -> {
            throw new BudgetExceededException(
                    new BigDecimal("10.00"), new BigDecimal("12.00"));
        };

        // Budget exception is not a provider problem —
        // fallback should not be attempted
        assertThatThrownBy(() -> module.process(REQUEST, budgetFailing))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void doesNotFallbackOnContextOverflow() {
        var module = new FallbackChainModule(List.of(successClient("anthropic")));

        PipelineChain contextFailing = request -> {
            throw new ContextOverflowException(200_000, 128_000);
        };

        assertThatThrownBy(() -> module.process(REQUEST, contextFailing))
                .isInstanceOf(ContextOverflowException.class);
    }

    @Test
    void stopsAtNonProviderErrorDuringFallback() {
        // First fallback throws a budget exception mid-chain —
        // should stop immediately, not try the second fallback
        var secondFallback = successClient("ollama");
        var module = new FallbackChainModule(List.of(
                failingClient("anthropic",
                        new BudgetExceededException(
                                new BigDecimal("10.00"), new BigDecimal("12.00"))),
                secondFallback));

        assertThatThrownBy(() -> module.process(REQUEST,
                failingChain(new ModelUnavailableException("gpt-4o", "openai"))))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void rejectsEmptyFallbackList() {
        assertThatThrownBy(() -> new FallbackChainModule(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFallbackList() {
        assertThatThrownBy(() -> new FallbackChainModule(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameIsCorrect() {
        var module = new FallbackChainModule(List.of(successClient("anthropic")));
        assertThat(module.name()).isEqualTo("fallback-chain");
    }
}
