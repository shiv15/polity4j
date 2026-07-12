package io.polity4j.adapters.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.ModelUnavailableException;
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

/**
 * Adapter for the OpenAI Chat Completions API.
 *
 * Translates OpenAI's specific JSON response payload and HTTP error codes
 * into Polity4j's core types and exception hierarchy.
 */
public final class OpenAiAdapter implements LlmClient {

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    public OpenAiAdapter(String apiKey) {
        this(HttpClient.newHttpClient(), apiKey, DEFAULT_API_URL);
    }

    public OpenAiAdapter(HttpClient httpClient, String apiKey) {
        this(httpClient, apiKey, DEFAULT_API_URL);
    }

    public OpenAiAdapter(HttpClient httpClient, String apiKey, String apiUrl) {
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

        String requestBody;
        try {
            requestBody = buildRequestBody(request);
        } catch (IOException e) {
            throw new ModelUnavailableException(request.model(), provider(), new RuntimeException("Failed to serialize request to JSON", e));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
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
            case 500, 503 -> throw new ModelUnavailableException(request.model(), provider(), new RuntimeException("Provider returned HTTP status: " + statusCode));
            default -> throw new ModelUnavailableException(request.model(), provider(),
                    new RuntimeException("Unexpected status: " + statusCode + " payload: " + httpResponse.body()));
        };
    }

    @Override
    public String provider() {
        return "openai";
    }

    private String buildRequestBody(LlmRequest request) throws IOException {
        List<OpenAiMessage> messages = new ArrayList<>();
        for (var msg : request.conversationHistory()) {
            messages.add(new OpenAiMessage(msg.role(), msg.content()));
        }
        messages.add(new OpenAiMessage("user", request.prompt()));

        OpenAiRequest apiRequest = new OpenAiRequest(
                request.model(),
                request.maxTokens() > 0 ? request.maxTokens() : null,
                messages
        );

        return objectMapper.writeValueAsString(apiRequest);
    }

    private LlmResponse parseSuccessResponse(String body, String model, long latencyMs) throws PolityException {
        try {
            OpenAiResponse response = objectMapper.readValue(body, OpenAiResponse.class);
            if (response.choices() == null || response.choices().isEmpty()) {
                throw new ModelUnavailableException(model, provider(), new RuntimeException("Response returned empty choices array: " + body));
            }

            Choice choice = response.choices().get(0);
            if (choice.message() == null) {
                throw new ModelUnavailableException(model, provider(), new RuntimeException("Response choices had no message: " + body));
            }

            String contentText = choice.message().content();
            int inputTokens = 0;
            int outputTokens = 0;
            if (response.usage() != null) {
                inputTokens = response.usage().promptTokens();
                outputTokens = response.usage().completionTokens();
            }

            // Pricing GPT-4o: $2.50 / M input, $10.00 / M output
            BigDecimal estimatedCost = BigDecimal.valueOf(inputTokens).multiply(new BigDecimal("0.0000025"))
                    .add(BigDecimal.valueOf(outputTokens).multiply(new BigDecimal("0.000010")));

            return LlmResponse.builder(contentText, model, provider())
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .estimatedCost(estimatedCost)
                    .latencyMs(latencyMs)
                    .build();
        } catch (IOException e) {
            throw new ModelUnavailableException(model, provider(), new RuntimeException("Failed to deserialize OpenAI success payload", e));
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
    private record OpenAiRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            List<OpenAiMessage> messages
    ) {}

    private record OpenAiMessage(String role, String content) {}

    private record OpenAiResponse(
            String id,
            String object,
            long created,
            String model,
            List<Choice> choices,
            Usage usage
    ) {}

    private record Choice(
            int index,
            OpenAiMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    private record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
