package io.polity4j.examples.full;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.polity4j.adapters.anthropic.AnthropicAdapter;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.cost.BudgetConfig;
import io.polity4j.cost.BudgetGuardrailModule;
import io.polity4j.cost.cache.ExactCacheModule;
import io.polity4j.cost.router.ModelRouterModule;
import io.polity4j.cost.router.RoutingPolicy;
import io.polity4j.integrations.micrometer.Polity4jMeterBinder;
import io.polity4j.quality.context.ContextWindowManagerModule;
import io.polity4j.quality.prompt.PromptOptimizerConfig;
import io.polity4j.quality.prompt.PromptOptimizerModule;
import io.polity4j.quality.response.ResponseValidatorModule;
import io.polity4j.quality.response.ValidationResult;
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerConfig;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;
import io.polity4j.reliability.fallback.FallbackChainModule;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Full End-to-End Polity4j Production Pipeline Demo.
 *
 * Demonstrates combining:
 *  - Quality: PromptOptimizerModule, ContextWindowManagerModule, ResponseValidatorModule
 *  - Cost: ModelRouterModule, ExactCacheModule, BudgetGuardrailModule
 *  - Reliability: RetryModule, CircuitBreakerModule, FallbackChainModule
 *  - Integrations: Micrometer Metrics Exporting
 */
public class FullPipelineDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        LlmClient primaryClient;
        LlmClient fallbackClient;

        if (apiKey != null && !apiKey.isBlank()) {
            primaryClient = new AnthropicAdapter(apiKey);
            fallbackClient = new AnthropicAdapter(apiKey);
        } else {
            System.out.println("[INFO] ANTHROPIC_API_KEY not set. Running with simulated LLM clients.");
            primaryClient = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest req) {
                    return LlmResponse.builder("Primary answer: " + req.prompt(), req.model(), provider())
                            .inputTokens(25).outputTokens(30).estimatedCost(new BigDecimal("0.00008")).latencyMs(150).build();
                }

                @Override
                public String provider() {
                    return "primary-provider";
                }
            };

            fallbackClient = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest req) {
                    return LlmResponse.builder("Fallback answer: " + req.prompt(), req.model(), provider())
                            .inputTokens(25).outputTokens(25).estimatedCost(new BigDecimal("0.00002")).latencyMs(80).build();
                }

                @Override
                public String provider() {
                    return "fallback-provider";
                }
            };
        }

        // 1. Quality Controls
        PromptOptimizerModule promptOptimizer = new PromptOptimizerModule(PromptOptimizerConfig.DEFAULT);
        ContextWindowManagerModule contextManager = new ContextWindowManagerModule();
        ResponseValidatorModule responseValidator = new ResponseValidatorModule(
                response -> response.content() != null && !response.content().isBlank()
                        ? ValidationResult.success()
                        : ValidationResult.failure("Content cannot be empty")
        );

        // 2. Cost Controls
        RoutingPolicy routingPolicy = RoutingPolicy.builder()
                .cheapModel("claude-3-haiku-20240307")
                .expensiveModel("claude-3-5-sonnet-20241022")
                .threshold(0.5)
                .build();
        ModelRouterModule modelRouter = new ModelRouterModule(routingPolicy);
        ExactCacheModule cacheModule = new ExactCacheModule();
        BudgetGuardrailModule budgetGuardrail = new BudgetGuardrailModule(
                new BudgetConfig(null, new BigDecimal("10.00"), null)
        );

        // 3. Reliability Controls
        RetryModule retryModule = new RetryModule(RetryConfig.builder().maxAttempts(3).initialDelay(Duration.ofMillis(300)).build());
        CircuitBreakerModule circuitBreaker = new CircuitBreakerModule("anthropic-breaker", CircuitBreakerConfig.DEFAULT);
        FallbackChainModule fallbackChain = new FallbackChainModule(List.of(fallbackClient));

        // 4. Build Pipeline
        LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
                .with(promptOptimizer)
                .with(contextManager)
                .with(responseValidator)
                .with(modelRouter)
                .with(cacheModule)
                .with(budgetGuardrail)
                .with(retryModule)
                .with(circuitBreaker)
                .with(fallbackChain)
                .build();

        // 5. Micrometer Metrics Exporting
        MeterRegistry registry = new SimpleMeterRegistry();
        Polity4jMeterBinder meterBinder = new Polity4jMeterBinder(cacheModule, circuitBreaker);
        meterBinder.bindTo(registry);

        System.out.println("=".repeat(60));
        System.out.println("Polity4j End-to-End Production Pipeline Demo");
        System.out.println("=".repeat(60));

        LlmRequest request = LlmRequest.builder("   Explain software architecture patterns concisely.   ", "claude-3-5-sonnet-20241022")
                .callerId("prod-service-1")
                .build();

        System.out.println("Executing Pipeline Request...");
        LlmResponse response = pipeline.execute(request);

        System.out.println("-".repeat(60));
        System.out.println("Response Provider : " + response.provider());
        System.out.println("Response Model    : " + response.model());
        System.out.println("Response Content  : " + response.content());
        System.out.println("Tokens            : " + response.inputTokens() + " in / " + response.outputTokens() + " out");
        System.out.println("Estimated Cost    : $" + response.estimatedCost());
        System.out.println("Latency           : " + response.latencyMs() + "ms");
        System.out.println("CB State          : " + circuitBreaker.state());

        System.out.println("-".repeat(60));
        System.out.println("Micrometer Metrics Summary:");
        registry.getMeters().forEach(meter -> System.out.println(" - " + meter.getId().getName() + " -> " + meter.getId().getType()));

        System.out.println("=".repeat(60));
    }
}
