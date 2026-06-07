package io.polity4j.examples.reliability;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal LlmClient implementation for the Anthropic Messages API.
 *
 * Uses Java's built-in HttpClient — no SDK, no extra dependencies.
 * This is intentional: it demonstrates that Polity4j works with any
 * HTTP client the caller chooses to bring.
 *
 * This class is part of the example only. In a real application you
 * would use your existing Anthropic SDK or HTTP client setup here.
 */
public final class AnthropicLlmClient implements LlmClient {

    private static final String API_URL =
            "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String modelOverride;
    private final HttpClient httpClient;

    public AnthropicLlmClient(String apiKey) {
        this(apiKey, null);
    }

    public AnthropicLlmClient(String apiKey, String modelOverride) {
        java.util.Objects.requireNonNull(apiKey, "apiKey must not be null");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey;
        this.modelOverride = modelOverride;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        java.util.Objects.requireNonNull(request, "request must not be null");
        if (request.maxTokens() <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }

        // Apply model override if present, otherwise use the request's model
        String modelToUse = (modelOverride != null) ? modelOverride : request.model();

        String body = buildRequestBody(request, modelToUse);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> httpResponse;
        long startMs = System.currentTimeMillis();

        try {
            httpResponse = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ModelUnavailableException(modelToUse, provider(), e);
        }

        long latencyMs = System.currentTimeMillis() - startMs;

        // Translate HTTP status codes into PolityException subtypes
        return switch (httpResponse.statusCode()) {
            case 200 -> parseSuccess(httpResponse.body(), modelToUse, latencyMs);
            case 429 -> throw new RateLimitException(provider(), parseRetryAfter(httpResponse));
            // Anthropic uses status code 529 specifically to indicate that the service is overloaded.
            case 529 -> throw new OverloadedException(provider());
            case 503, 500 -> throw new ModelUnavailableException(modelToUse, provider());
            default -> throw new ModelUnavailableException(
                    modelToUse, provider(),
                    new RuntimeException("Unexpected status: "
                            + httpResponse.statusCode()
                            + " body: " + httpResponse.body()));
        };
    }

    @Override
    public String provider() { return "anthropic"; }

    // -------------------------------------------------------------------------
    // Minimal JSON handling — no library needed for this simple structure
    // -------------------------------------------------------------------------

    private String buildRequestBody(LlmRequest request, String model) {
        // Escape the prompt for JSON
        String escapedPrompt = request.prompt()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        return """
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(model, request.maxTokens(), escapedPrompt);
    }

    private LlmResponse parseSuccess(String body, String model, long latencyMs) {
        // Extract content text from the response JSON.
        // Anthropic response shape:
        // {"content": [{"type": "text", "text": "..."}], "usage": {...}}
        String content = extractJsonString(body, "\"text\":");
        int inputTokens = extractJsonInt(body, "\"input_tokens\":");
        int outputTokens = extractJsonInt(body, "\"output_tokens\":");

        // Approximate cost: claude-3-5-sonnet pricing as of 2025
        // $3 per 1M input tokens, $15 per 1M output tokens
        BigDecimal cost = BigDecimal.valueOf(inputTokens * 0.000003)
                .add(BigDecimal.valueOf(outputTokens * 0.000015));

        return LlmResponse.builder(content, model, provider())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCost(cost)
                .latencyMs(latencyMs)
                .build();
    }

    private String extractJsonString(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return "";
        int start = json.indexOf("\"", keyIndex + key.length()) + 1;
        int end = json.indexOf("\"", start);
        return start > 0 && end > start ? json.substring(start, end) : "";
    }

    private int extractJsonInt(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return 0;
        int start = keyIndex + key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseRetryAfter(HttpResponse<String> response) {
        return response.headers()
                .firstValue("retry-after")
                .map(v -> {
                    try { return Long.parseLong(v) * 1000L; }
                    catch (NumberFormatException e) { return 5000L; }
                })
                .orElse(5000L);
    }
}
