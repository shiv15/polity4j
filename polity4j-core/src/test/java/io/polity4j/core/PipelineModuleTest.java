package io.polity4j.core;

import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineModuleTest {

    // A no-op module that just passes through to next
    static class PassthroughModule implements PipelineModule {
        boolean wasCalled = false;

        @Override
        public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
            wasCalled = true;
            return next.proceed(request);
        }

        @Override
        public String name() { return "passthrough"; }
    }

    // A module that short-circuits and returns without calling next
    static class ShortCircuitModule implements PipelineModule {
        @Override
        public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
            return LlmResponse.builder("cached", request.model(), "cache")
                    .build();
        }

        @Override
        public String name() { return "short-circuit"; }
    }

    @Test
    void passthroughModuleCallsNext() throws PolityException {
        var module = new PassthroughModule();
        var terminal = terminalChain();
        var request = LlmRequest.builder("Hello", "gpt-4o").build();

        var response = module.process(request, terminal);

        assertThat(module.wasCalled).isTrue();
        assertThat(response.content()).isEqualTo("terminal-response");
    }

    @Test
    void shortCircuitModuleDoesNotCallNext() throws PolityException {
        var module = new ShortCircuitModule();
        PipelineChain shouldNotBeCalled = req -> {
            throw new AssertionError("next should not have been called");
        };
        var request = LlmRequest.builder("Hello", "gpt-4o").build();

        var response = module.process(request, shouldNotBeCalled);

        assertThat(response.content()).isEqualTo("cached");
        assertThat(response.provider()).isEqualTo("cache");
    }

    private PipelineChain terminalChain() {
        return request -> LlmResponse.builder("terminal-response", request.model(), "stub")
                .inputTokens(10)
                .outputTokens(20)
                .estimatedCost(new BigDecimal("0.001"))
                .latencyMs(100)
                .build();
    }
}
