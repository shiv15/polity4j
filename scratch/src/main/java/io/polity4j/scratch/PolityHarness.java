package io.polity4j.scratch;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;

import java.time.Duration;

/**
 * Native harness for Polity4j.
 * Configured with normalized backoff parameters:
 * maxAttempts=3, initialDelay=100ms, multiplier=2.0, maxDelay=2000ms.
 */
public final class PolityHarness {

    private final RetryModule retryModule;

    public PolityHarness() {
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(100))
                .multiplier(2.0)
                .maxDelay(Duration.ofMillis(2000))
                .build();
        this.retryModule = new RetryModule(config);
    }

    public LlmResponse execute(LlmRequest request, PipelineChain terminalChain) {
        return retryModule.process(request, terminalChain);
    }
}
