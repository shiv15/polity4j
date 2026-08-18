package io.polity4j.examples.full;

import io.polity4j.adapters.anthropic.AnthropicAdapter;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Demonstrates real-time token streaming with polity4j pipeline controls.
 */
public class StreamingPipelineDemo {

    public static void main(String[] args) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        LlmClient client;
        if (apiKey != null && !apiKey.isBlank()) {
            client = new AnthropicAdapter(apiKey);
        } else {
            System.out.println("[INFO] ANTHROPIC_API_KEY not set. Running with simulated streaming LLM client.");
            client = new LlmClient() {
                @Override
                public LlmResponse call(LlmRequest request) {
                    return LlmResponse.builder("Simulated full response", request.model(), provider()).build();
                }

                @Override
                public LlmResponse callStreaming(LlmRequest request, Consumer<String> tokenHandler) {
                    String[] words = {"Polity4j ", "real-time ", "streaming ", "engine ", "is ", "active!"};
                    for (String word : words) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        if (tokenHandler != null) {
                            tokenHandler.accept(word);
                        }
                    }
                    return LlmResponse.builder("Polity4j real-time streaming engine is active!", request.model(), provider())
                            .inputTokens(12)
                            .outputTokens(8)
                            .estimatedCost(new BigDecimal("0.00003"))
                            .build();
                }

                @Override
                public String provider() {
                    return "streaming-simulator";
                }
            };
        }

        LlmPipeline pipeline = LlmPipeline.builder(client).build();

        System.out.println("=".repeat(60));
        System.out.println("Polity4j Real-Time Streaming & Token Callback Demo");
        System.out.println("=".repeat(60));

        LlmRequest request = LlmRequest.builder("Write a short poem about clean code.", "claude-3-5-sonnet-20241022").build();

        System.out.print("Streaming output: ");
        LlmResponse response = pipeline.executeStreaming(request, token -> {
            System.out.print(token);
            System.out.flush();
        });

        System.out.println("\n" + "-".repeat(60));
        System.out.println("Full Aggregated Content : " + response.content());
        System.out.println("Provider                : " + response.provider());
        System.out.println("Finish Reason           : " + response.finishReason());
        System.out.println("Estimated Cost          : $" + response.estimatedCost());
        System.out.println("=".repeat(60));
    }
}
