package io.polity4j.examples.reliability;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerConfig;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;
import io.polity4j.reliability.fallback.FallbackChainModule;

import java.time.Duration;
import java.util.List;

/**
 * Demonstrates a real Polity4j reliability pipeline against the Anthropic API.
 *
 * Pipeline structure:
 *
 *   RetryModule
 *       ↓ on transient failure
 *   CircuitBreakerModule
 *       ↓ on sustained failure
 *   FallbackChainModule
 *       ↓ on provider exhaustion
 *   AnthropicLlmClient (primary)
 *
 * The fallback in this demo is a second AnthropicLlmClient pointing at
 * a different model — in a real setup this would be a different provider
 * entirely (OpenAI, Ollama, etc).
 *
 * Run with:
 *   export ANTHROPIC_API_KEY=your-key
 *   mvn exec:java -pl polity4j-examples/reliability-demo
 */
public class ReliabilityDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: ANTHROPIC_API_KEY environment variable not set.");
            System.err.println("See README.md for setup instructions.");
            System.exit(1);
        }

        // Primary client — claude-3-5-sonnet
        LlmClient primaryClient =
                new AnthropicLlmClient(apiKey);

        // Fallback client — claude-3-haiku (cheaper, faster model)
        // In a real setup this would be a completely different provider
        LlmClient fallbackClient =
                new AnthropicLlmClient(apiKey, "claude-3-haiku-20240307");

        // Retry: up to 3 attempts, 500ms initial delay, exponential backoff
        RetryModule retryModule = new RetryModule(
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialDelay(Duration.ofMillis(500))
                        .multiplier(2.0)
                        .build());

        // Circuit breaker: trip after 5 failures, 30s cooldown
        CircuitBreakerModule circuitBreakerModule = new CircuitBreakerModule(
                "anthropic",
                CircuitBreakerConfig.builder()
                        .failureThreshold(5)
                        .cooldownDuration(Duration.ofSeconds(30))
                        .build());

        // Fallback chain: try fallback client if primary is unavailable
        FallbackChainModule fallbackChainModule =
                new FallbackChainModule(List.of(fallbackClient));

        // Wire the pipeline
        LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
                .with(retryModule)
                .with(circuitBreakerModule)
                .with(fallbackChainModule)
                .build();

        // Build the request
        LlmRequest request = LlmRequest.builder(
                "Explain in one sentence why reliability matters in distributed systems.",
                "claude-3-5-sonnet-20241022")
                .maxTokens(256)
                .callerId("reliability-demo")
                .build();

        // Execute and print results
        System.out.println("=".repeat(60));
        System.out.println("Polity4j Reliability Demo");
        System.out.println("=".repeat(60));
        System.out.println("Prompt : " + request.prompt());
        System.out.println("Model  : " + request.model());
        System.out.println("-".repeat(60));

        try {
            LlmResponse response = pipeline.execute(request);

            System.out.println("Response  : " + response.content());
            System.out.println("Provider  : " + response.provider());
            System.out.println("Tokens    : " + response.inputTokens()
                    + " in / " + response.outputTokens() + " out");
            System.out.println("Cost      : $" + response.estimatedCost());
            System.out.println("Latency   : " + response.latencyMs() + "ms");
            System.out.println("CB State  : " + circuitBreakerModule.state());

        } catch (PolityException e) {
            System.err.println("Pipeline failed: " + e.getMessage());
            System.err.println("Exception type: " + e.getClass().getSimpleName());
            System.exit(1);
        }

        System.out.println("=".repeat(60));
    }
}
