package io.polity4j.adapters.openai;

import com.sun.net.httpserver.HttpServer;
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
}
