package io.polity4j.examples.quality;

import io.polity4j.adapters.anthropic.AnthropicAdapter;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.quality.context.ContextWindowManagerConfig;
import io.polity4j.quality.context.ContextWindowManagerModule;
import io.polity4j.quality.prompt.PromptOptimizerConfig;
import io.polity4j.quality.prompt.PromptOptimizerModule;
import io.polity4j.quality.response.ResponseValidator;
import io.polity4j.quality.response.ResponseValidatorModule;
import io.polity4j.quality.response.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates Polity4j Quality Control pipeline:
 *
 * 1. PromptOptimizerModule: Stripping whitespace and normalizing formatting.
 * 2. ContextWindowManagerModule: Managing long conversation context windows.
 * 3. ResponseValidatorModule: Schema validation with automatic corrective retry loops.
 */
public class QualityDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        AtomicInteger attemptCount = new AtomicInteger(0);

        LlmClient client;
        if (apiKey != null && !apiKey.isBlank()) {
            client = new AnthropicAdapter(apiKey);
        } else {
            System.out.println("[INFO] ANTHROPIC_API_KEY not set. Running with simulated LLM client.");
            client = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest request) {
                    int attempt = attemptCount.incrementAndGet();
                    String content;
                    if (attempt == 1) {
                        content = "Sure! Here is your result: { status: ok }";
                    } else {
                        content = "{\"status\": \"success\", \"message\": \"Valid JSON payload\"}";
                    }
                    return LlmResponse.builder(content, request.model(), provider())
                            .inputTokens(20)
                            .outputTokens(15)
                            .estimatedCost(new BigDecimal("0.00004"))
                            .latencyMs(100)
                            .build();
                }

                @Override
                public String provider() {
                    return "simulated-provider";
                }
            };
        }

        // 1. Prompt Optimizer
        PromptOptimizerModule promptOptimizer = new PromptOptimizerModule(PromptOptimizerConfig.DEFAULT);

        // 2. Context Window Manager: Max character budget for history
        ContextWindowManagerConfig contextConfig = ContextWindowManagerConfig.builder()
                .maxHistoryChars(500)
                .windowSize(2)
                .build();
        ContextWindowManagerModule contextManager = new ContextWindowManagerModule(contextConfig);

        // 3. Response Validator: Ensure response starts with '{' and ends with '}'
        ResponseValidator jsonValidator = response -> {
            String content = response.content().trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                return ValidationResult.success();
            } else {
                return ValidationResult.failure("Response is not valid JSON format. Return raw JSON enclosed in {}");
            }
        };
        ResponseValidatorModule responseValidator = new ResponseValidatorModule(jsonValidator);

        LlmPipeline pipeline = LlmPipeline.builder(client)
                .with(promptOptimizer)
                .with(contextManager)
                .with(responseValidator)
                .build();

        System.out.println("=".repeat(60));
        System.out.println("Polity4j Quality Control Demo");
        System.out.println("=".repeat(60));

        LlmRequest request = LlmRequest.builder(
                        "   Generate a JSON status   response   with status and message.   ",
                        "claude-3-5-sonnet-20241022")
                .conversationHistory(List.of(
                        new LlmRequest.Message("user", "System boot initialized."),
                        new LlmRequest.Message("assistant", "System boot complete.")
                ))
                .build();

        System.out.println("Original Prompt: '" + request.prompt() + "'");

        LlmResponse response = pipeline.execute(request);

        System.out.println("-".repeat(60));
        System.out.println("Final Validated Response: " + response.content());
        System.out.println("Total Execution Attempts : " + (apiKey != null ? "1 (Real API)" : attemptCount.get() + " (Corrective Retry Succeeded!)"));
        System.out.println("=".repeat(60));
    }
}
