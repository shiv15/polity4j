package io.polity4j.adapters.anthropic;

import com.sun.net.httpserver.HttpServer;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicAdapterTest {

    private HttpServer server;
    private String serverUrl;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicReference<String> retryAfterHeader = new AtomicReference<>(null);
    private final AtomicReference<String> capturedRequestBody = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
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
        serverUrl = "http://localhost:" + port + "/v1/messages";
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
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {"type": "text", "text": "Hello user!"}
                  ],
                  "model": "claude-3-5-sonnet",
                  "stop_reason": "end_turn",
                  "usage": {
                    "input_tokens": 100,
                    "output_tokens": 50
                  }
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        AnthropicAdapter adapter = new AnthropicAdapter(HttpClient.newHttpClient(), "test-key", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "claude-3-5-sonnet").build();

        LlmResponse response = adapter.call(request);

        assertThat(response.content()).isEqualTo("Hello user!");
        assertThat(response.model()).isEqualTo("claude-3-5-sonnet");
        assertThat(response.provider()).isEqualTo("anthropic");
        assertThat(response.inputTokens()).isEqualTo(100);
        assertThat(response.outputTokens()).isEqualTo(50);
        // Cost: 100 * 0.000003 + 50 * 0.000015 = 0.0003 + 0.00075 = 0.00105
        assertThat(response.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.00105"));
    }

    @Test
    void testSystemPromptHandling() {
        String successJson = """
                {
                  "id": "msg_01",
                  "content": [{"type": "text", "text": "OK"}],
                  "model": "claude-3-5-sonnet",
                  "usage": {"input_tokens": 1, "output_tokens": 1}
                }
                """;
        responseStatus.set(200);
        responseBody.set(successJson);

        AnthropicAdapter adapter = new AnthropicAdapter(HttpClient.newHttpClient(), "test-key", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "claude-3-5-sonnet")
                .conversationHistory(List.of(
                        new LlmRequest.Message("system", "You are a helpful assistant."),
                        new LlmRequest.Message("system", "Be concise.")
                ))
                .build();

        adapter.call(request);

        String captured = capturedRequestBody.get();
        // System parameter should exist and contain system prompts joined by newline
        assertThat(captured).contains("\"system\":\"You are a helpful assistant.\\nBe concise.\"");
        // Ensure system messages are filtered out from "messages" array
        assertThat(captured).doesNotContain("{\"role\":\"system\"");
    }

    @Test
    void testRateLimitException() {
        responseStatus.set(429);
        retryAfterHeader.set("3");
        responseBody.set("{\"error\": \"rate_limit\"}");

        AnthropicAdapter adapter = new AnthropicAdapter(HttpClient.newHttpClient(), "test-key", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "claude-3-5-sonnet").build();

        assertThatThrownBy(() -> adapter.call(request))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Rate limit exceeded")
                .extracting(e -> ((RateLimitException) e).retryAfterMs())
                .isEqualTo(3000L);
    }

    @Test
    void testOverloadedException() {
        responseStatus.set(529);
        responseBody.set("{\"error\": \"overloaded\"}");

        AnthropicAdapter adapter = new AnthropicAdapter(HttpClient.newHttpClient(), "test-key", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "claude-3-5-sonnet").build();

        assertThatThrownBy(() -> adapter.call(request))
                .isInstanceOf(OverloadedException.class)
                .hasMessageContaining("Provider 'anthropic' is overloaded");
    }

    @Test
    void testModelUnavailableException() {
        responseStatus.set(503);
        responseBody.set("{\"error\": \"unavailable\"}");

        AnthropicAdapter adapter = new AnthropicAdapter(HttpClient.newHttpClient(), "test-key", serverUrl);
        LlmRequest request = LlmRequest.builder("Hi", "claude-3-5-sonnet").build();

        assertThatThrownBy(() -> adapter.call(request))
                .isInstanceOf(ModelUnavailableException.class)
                .cause()
                .hasMessageContaining("Provider returned HTTP status: 503");
    }
}
