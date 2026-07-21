package io.polity4j.examples.cost;

import io.polity4j.adapters.anthropic.AnthropicAdapter;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.BudgetExceededException;
import io.polity4j.cost.BudgetConfig;
import io.polity4j.cost.BudgetGuardrailModule;
import io.polity4j.cost.cache.ExactCacheModule;
import io.polity4j.cost.router.ModelRouterModule;
import io.polity4j.cost.router.RoutingPolicy;

import java.math.BigDecimal;

/**
 * Demonstrates Polity4j Cost Optimization pipeline:
 *
 * 1. ExactCacheModule: Instant short-circuiting for identical prompts (0ms latency, $0 cost).
 * 2. ModelRouterModule: Routing simple prompts to cheap model (claude-3-haiku) vs. expensive model (claude-3-5-sonnet).
 * 3. BudgetGuardrailModule: Enforcing cumulative spend limits per call, caller, or organization.
 */
public class CostDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        LlmClient client;
        if (apiKey != null && !apiKey.isBlank()) {
            client = new AnthropicAdapter(apiKey);
        } else {
            System.out.println("[INFO] ANTHROPIC_API_KEY not set. Running with simulated LLM client.");
            client = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest request) {
                    return LlmResponse.builder(
                                    "Simulated answer to: " + request.prompt(),
                                    request.model(),
                                    provider())
                            .inputTokens(15)
                            .outputTokens(20)
                            .estimatedCost(new BigDecimal("0.00005"))
                            .latencyMs(120)
                            .build();
                }

                @Override
                public String provider() {
                    return "simulated-provider";
                }
            };
        }

        // 1. Exact Cache
        ExactCacheModule cacheModule = new ExactCacheModule();

        // 2. Model Router: Threshold 0.5 — simple prompts go to haiku, complex prompts go to sonnet
        RoutingPolicy routingPolicy = RoutingPolicy.builder()
                .cheapModel("claude-3-haiku-20240307")
                .expensiveModel("claude-3-5-sonnet-20241022")
                .threshold(0.5)
                .build();
        ModelRouterModule routerModule = new ModelRouterModule(routingPolicy);

        // 3. Budget Guardrail: max $0.0001 per caller
        BudgetConfig budgetConfig = new BudgetConfig(null, new BigDecimal("0.0001"), null);
        BudgetGuardrailModule budgetModule = new BudgetGuardrailModule(budgetConfig);

        LlmPipeline pipeline = LlmPipeline.builder(client)
                .with(routerModule)
                .with(cacheModule)
                .with(budgetModule)
                .build();

        System.out.println("=".repeat(60));
        System.out.println("Polity4j Cost Optimization Demo");
        System.out.println("=".repeat(60));

        // Test 1: Cache Miss vs Cache Hit
        System.out.println("\n--- Step 1: Exact Cache Test ---");
        LlmRequest request1 = LlmRequest.builder("What is 2 + 2?", "claude-3-5-sonnet-20241022")
                .callerId("user-123")
                .build();

        LlmResponse res1 = pipeline.execute(request1);
        System.out.println("Call 1 (Cache Miss): latency=" + res1.latencyMs() + "ms, model=" + res1.model());

        LlmResponse res2 = pipeline.execute(request1);
        System.out.println("Call 2 (Cache Hit) : latency=" + res2.latencyMs() + "ms, model=" + res2.model());

        // Test 2: Model Routing
        System.out.println("\n--- Step 2: Model Router Test ---");
        LlmRequest simpleReq = LlmRequest.builder("Hello", "claude-3-5-sonnet-20241022")
                .callerId("user-456")
                .build();
        LlmResponse simpleRes = pipeline.execute(simpleReq);
        System.out.println("Simple Prompt routed to model: " + simpleRes.model());

        // Test 3: Budget Guardrail Breach
        System.out.println("\n--- Step 3: Budget Guardrail Test ---");
        try {
            LlmRequest expensiveReq = LlmRequest.builder("Explain quantum field theory in detail.", "claude-3-5-sonnet-20241022")
                    .callerId("user-123")
                    .build();
            pipeline.execute(expensiveReq);
            System.out.println("Request executed within budget.");
        } catch (BudgetExceededException e) {
            System.out.println("Budget Guardrail Intercepted Call!");
            System.out.println("  Ceiling   : $" + e.ceiling());
            System.out.println("  Projected : $" + e.projected());
        }

        System.out.println("=".repeat(60));
    }
}
