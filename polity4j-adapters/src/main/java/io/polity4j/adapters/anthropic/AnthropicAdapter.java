package io.polity4j.adapters.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.RateLimitException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter for the Anthropic Messages API.
 *
 * Translates Anthropic's specific JSON response payload and HTTP error codes
 * into Polity4j's core types and exception hierarchy.
 */
public final class AnthropicAdapter implements LlmClient {

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION_HEADER = "anthropic-version";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    public AnthropicAdapter(String apiKey) {
        this(HttpClient.newHttpClient(), apiKey, DEFAULT_API_URL);
    }

    public AnthropicAdapter(HttpClient httpClient, String apiKey) {
        this(httpClient, apiKey, DEFAULT_API_URL);
    }

    public AnthropicAdapter(HttpClient httpClient, String apiKey, String apiUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiUrl = Objects.requireNonNull(apiUrl, "apiUrl must not be null");
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");

        // Format message request
        String requestBody;
        try {
            requestBody = buildRequestBody(request);
        } catch (IOException e) {
            throw new ModelUnavailableException(request.model(), provider(), new RuntimeException("Failed to serialize request to JSON", e));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ModelUnavailableException(request.model(), provider(), new RuntimeException("HTTP call interrupted or connection failed", e));
        }
        long latencyMs = System.currentTimeMillis() - startTime;

        int statusCode = httpResponse.statusCode();

        return switch (statusCode) {
            case 200 -> parseSuccessResponse(httpResponse.body(), request.model(), latencyMs);
            case 429 -> throw new RateLimitException(provider(), parseRetryAfter(httpResponse));
            case 529 -> throw new OverloadedException(provider());
            case 500, 503 -> throw new ModelUnavailableException(request.model(), provider(), new RuntimeException("Provider returned HTTP status: " + statusCode));
            default -> throw new ModelUnavailableException(request.model(), provider(),
                    new RuntimeException("Unexpected status: " + statusCode + " payload: " + httpResponse.body()));
        };
    }

    @Override
    public String provider() {
        return "anthropic";
    }

    private String buildRequestBody(LlmRequest request) throws IOException {
        // Anthropic requires system prompt as root field, and system roles filtered out of messages array.
        String systemPrompt = request.conversationHistory().stream()
                .filter(m -> "system".equalsIgnoreCase(m.role()))
                .map(LlmRequest.Message::content)
                .collect(Collectors.joining("\n"));

        List<AnthropicMessage> messages = request.conversationHistory().stream()
                .filter(m -> !"system".equalsIgnoreCase(m.role()))
                .map(m -> new AnthropicMessage(m.role(), m.content()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Add the current prompt turn
        messages.add(new AnthropicMessage("user", request.prompt()));

        AnthropicRequest apiRequest = new AnthropicRequest(
                request.model(),
                request.maxTokens(),
                systemPrompt.isBlank() ? null : systemPrompt,
                messages
        );

        return objectMapper.writeValueAsString(apiRequest);
    }

    private LlmResponse parseSuccessResponse(String body, String model, long latencyMs) throws PolityException {
        try {
            AnthropicResponse response = objectMapper.readValue(body, AnthropicResponse.class);
            if (response.content() == null || response.content().isEmpty()) {
                throw new ModelUnavailableException(model, provider(), new RuntimeException("Response returned empty content array: " + body));
            }

            String contentText = response.content().get(0).text();
            int inputTokens = 0;
            int outputTokens = 0;
            if (response.usage() != null) {
                inputTokens = response.usage().inputTokens();
                outputTokens = response.usage().outputTokens();
            }

            // Pricing Claude 3.5 Sonnet: $3.00 / M input, $15.00 / M output
            BigDecimal estimatedCost = BigDecimal.valueOf(inputTokens).multiply(new BigDecimal("0.000003"))
                    .add(BigDecimal.valueOf(outputTokens).multiply(new BigDecimal("0.000015")));

            return LlmResponse.builder(contentText, model, provider())
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .estimatedCost(estimatedCost)
                    .latencyMs(latencyMs)
                    .build();
        } catch (IOException e) {
            throw new ModelUnavailableException(model, provider(), new RuntimeException("Failed to deserialize Anthropic success payload", e));
        }
    }

    private long parseRetryAfter(HttpResponse<String> response) {
        return response.headers()
                .firstValue("retry-after")
                .map(v -> {
                    try {
                        return Long.parseLong(v) * 1000L;
                    } catch (NumberFormatException e) {
                        return 5000L;
                    }
                })
                .orElse(5000L);
    }

    // JSON mapping helper records
    private record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<AnthropicMessage> messages
    ) {}

    private record AnthropicMessage(String role, String content) {}

    private record AnthropicResponse(
            String id,
            String type,
            String role,
            List<AnthropicContent> content,
            String model,
            @JsonProperty("stop_reason") String stopReason,
            Usage usage
    ) {}

    private record AnthropicContent(String type, String text) {}

    private record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}
}
