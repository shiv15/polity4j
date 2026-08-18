package io.polity4j.adapters.openai;

import com.sun.net.httpserver.HttpServer;
import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.RateLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiAdapterTest {

    private HttpServer server;
    private String serverUrl;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicReference<String> retryAfterHeader = new AtomicReference<>(null);
    private final AtomicReference<String> capturedRequestBody = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            // Capture request body
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            capturedRequestBody.set(new String(bytes, StandardCharsets.UTF_8));

            // Set headers
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (retryAfterHeader.get() != null) {
                exchange.getResponseHeaders().set("retry-after", retryAfterHeader.get());
            }

            // Write response
            byte[] responseBytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        serverUrl = "http://localhost:" + port + "/v1/chat/completions";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSuccessResponse() {
        String successJson = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "created": 1677652288,
                  "model": "gpt-4o",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello user from OpenAI!"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 80,
                    "completion_tokens": 40,
                    "total_tokens": 120
                  }
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o")
                .conversationHistory(List.of(
                        new LlmRequest.Message("system", "Act as a code assistant.")
                ))
                .build();

        LlmResponse response = adapter.call(request);

        assertThat(response.content()).isEqualTo("Hello user from OpenAI!");
        assertThat(response.model()).isEqualTo("gpt-4o");
        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.inputTokens()).isEqualTo(80);
        assertThat(response.outputTokens()).isEqualTo(40);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        // Cost: 80 * 0.0000025 + 40 * 0.000010 = 0.0002 + 0.0004 = 0.0006
        assertThat(response.estimatedCost()).isEqualByComparingTo(new java.math.BigDecimal("0.0006"));

        String captured = capturedRequestBody.get();
        // Messages list should contain system instruction and user prompts
        assertThat(captured).contains("{\"role\":\"system\",\"content\":\"Act as a code assistant.\"}");
        assertThat(captured).contains("{\"role\":\"user\",\"content\":\"Hi\"}");
    }

    @Test
    void testRateLimitException() {
        responseStatus.set(429);
        retryAfterHeader.set("5");
        responseBody.set("{\"error\": \"rate_limit\"}");

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o").build();

        assertThatThrownBy(() -> adapter.call(request))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded")
                .extracting(e -> ((RateLimitException) e).retryAfterMs())
                .isEqualTo(5000L);
    }

    @Test
    void testModelUnavailableException() {
        responseStatus.set(500);
        responseBody.set("{\"error\": \"internal_error\"}");

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o").build();

        assertThatThrownBy(() -> adapter.call(request))
                .isInstanceOf(ModelUnavailableException.class)
                .cause()
                .hasMessageContaining("Provider returned HTTP status: 500");
    }

    @Test
    void testGenerationParametersAndAdditionalParamsSerialization() {
        String successJson = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "OK"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o")
                .temperature(0.7)
                .topP(0.9)
                .frequencyPenalty(0.5)
                .presencePenalty(0.2)
                .additionalParam("user", "user_abc")
                .additionalParam("seed", 1234)
                .build();

        adapter.call(request);

        String captured = capturedRequestBody.get();
        assertThat(captured).contains("\"temperature\":0.7");
        assertThat(captured).contains("\"top_p\":0.9");
        assertThat(captured).contains("\"frequency_penalty\":0.5");
        assertThat(captured).contains("\"presence_penalty\":0.2");
        assertThat(captured).contains("\"user\":\"user_abc\"");
        assertThat(captured).contains("\"seed\":1234");
    }

    @Test
    void testFinishReasonMapping() {
        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o").build();

        responseStatus.set(200);

        responseBody.set("{\"choices\":[{\"message\":{\"content\":\"a\"},\"finish_reason\":\"length\"}]}");
        assertThat(adapter.call(request).finishReason()).isEqualTo(FinishReason.LENGTH);

        responseBody.set("{\"choices\":[{\"message\":{\"content\":\"b\"},\"finish_reason\":\"content_filter\"}]}");
        assertThat(adapter.call(request).finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);

        responseBody.set("{\"choices\":[{\"message\":{\"content\":\"c\"},\"finish_reason\":\"tool_calls\"}]}");
        assertThat(adapter.call(request).finishReason()).isEqualTo(FinishReason.TOOL_CALLS);

        responseBody.set("{\"choices\":[{\"message\":{\"content\":\"d\"},\"finish_reason\":\"unknown_reason\"}]}");
        assertThat(adapter.call(request).finishReason()).isEqualTo(FinishReason.UNKNOWN);
    }

    @Test
    void testExplicitSystemPromptHandling() {
        String successJson = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "OK"}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o")
                .systemPrompt("You are an expert Java architect.")
                .build();

        adapter.call(request);

        String captured = capturedRequestBody.get();
        assertThat(captured).contains("{\"role\":\"system\",\"content\":\"You are an expert Java architect.\"}");
        assertThat(captured).contains("{\"role\":\"user\",\"content\":\"Hi\"}");
    }

    @Test
    void testMultimodalImageMessage() {
        String successJson = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "It is a logo."}}],
                  "usage": {"prompt_tokens": 100, "completion_tokens": 10, "total_tokens": 110}
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        var textPart = new io.polity4j.core.ContentPart.TextContentPart("Analyze this:");
        var imagePart = io.polity4j.core.ContentPart.ImageContentPart.ofUrl("https://example.com/logo.png");
        LlmRequest request = LlmRequest.builder("Final prompt", "gpt-4o")
                .conversationHistory(List.of(new LlmRequest.Message("user", List.of(textPart, imagePart))))
                .build();

        adapter.call(request);

        String captured = capturedRequestBody.get();
        assertThat(captured).contains("\"type\":\"image_url\"");
        assertThat(captured).contains("\"url\":\"https://example.com/logo.png\"");
        assertThat(captured).contains("\"text\":\"Analyze this:\"");
    }

    @Test
    void testStreamingSseResponse() {
        String ssePayload = """
                data: {"choices":[{"delta":{"content":"Hello "}}]}

                data: {"choices":[{"delta":{"content":"world!"},"finish_reason":"stop"}]}

                data: [DONE]
                """;
        responseStatus.set(200);
        responseBody.set(ssePayload);

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), "test-key-openai", serverUrl);
        var tokens = new ArrayList<String>();
        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o").build();

        var response = adapter.callStreaming(request, tokens::add);

        assertThat(tokens).containsExactly("Hello ", "world!");
        assertThat(response.content()).isEqualTo("Hello world!");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);

        String captured = capturedRequestBody.get();
        assertThat(captured).contains("\"stream\":true");
    }
}

