package io.polity4j.examples.adapters;

import io.polity4j.adapters.anthropic.AnthropicAdapter;
import io.polity4j.adapters.openai.OpenAiAdapter;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;

import java.math.BigDecimal;

/**
 * Demonstrates using Polity4j built-in adapters (AnthropicAdapter and OpenAiAdapter)
 * with JDK 11+ HttpClient and Jackson parsing.
 */
public class AdaptersDemo {

    public static void main(String[] args) {
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        String openAiKey = System.getenv("OPENAI_API_KEY");

        System.out.println("=".repeat(60));
        System.out.println("Polity4j Adapters Demo");
        System.out.println("=".repeat(60));

        // 1. Anthropic Adapter
        System.out.println("\n--- Anthropic Adapter ---");
        LlmClient anthropicClient;
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            anthropicClient = new AnthropicAdapter(anthropicKey);
        } else {
            System.out.println("[INFO] ANTHROPIC_API_KEY not set. Using simulated Anthropic client.");
            anthropicClient = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest req) {
                    return LlmResponse.builder("Simulated Anthropic response for: " + req.prompt(), req.model(), provider())
                            .inputTokens(12).outputTokens(18).estimatedCost(new BigDecimal("0.00003")).latencyMs(90).build();
                }

                @Override
                public String provider() {
                    return "anthropic";
                }
            };
        }

        LlmPipeline anthropicPipeline = LlmPipeline.builder(anthropicClient)
                .with(new RetryModule(RetryConfig.DEFAULT))
                .build();

        LlmRequest anthropicReq = LlmRequest.builder("Explain concurrency in Java in one sentence.", "claude-3-5-sonnet-20241022").build();
        LlmResponse anthropicRes = anthropicPipeline.execute(anthropicReq);
        System.out.println("Provider : " + anthropicRes.provider());
        System.out.println("Model    : " + anthropicRes.model());
        System.out.println("Content  : " + anthropicRes.content());

        // 2. OpenAI Adapter
        System.out.println("\n--- OpenAI Adapter ---");
        LlmClient openAiClient;
        if (openAiKey != null && !openAiKey.isBlank()) {
            openAiClient = new OpenAiAdapter(openAiKey);
        } else {
            System.out.println("[INFO] OPENAI_API_KEY not set. Using simulated OpenAI client.");
            openAiClient = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest req) {
                    return LlmResponse.builder("Simulated OpenAI response for: " + req.prompt(), req.model(), provider())
                            .inputTokens(14).outputTokens(22).estimatedCost(new BigDecimal("0.00004")).latencyMs(110).build();
                }

                @Override
                public String provider() {
                    return "openai";
                }
            };
        }

        LlmPipeline openAiPipeline = LlmPipeline.builder(openAiClient)
                .with(new RetryModule(RetryConfig.DEFAULT))
                .build();

        LlmRequest openAiReq = LlmRequest.builder("Explain immutability in Java in one sentence.", "gpt-4o").build();
        LlmResponse openAiRes = openAiPipeline.execute(openAiReq);
        System.out.println("Provider : " + openAiRes.provider());
        System.out.println("Model    : " + openAiRes.model());
        System.out.println("Content  : " + openAiRes.content());

        System.out.println("=".repeat(60));
    }
}
