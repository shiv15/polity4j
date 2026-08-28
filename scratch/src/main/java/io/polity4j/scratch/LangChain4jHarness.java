package io.polity4j.scratch;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

/**
 * Native harness for LangChain4j.
 * Points to WireMock backend HTTP URL and configures maxRetries(3).
 * Note: LangChain4j's internal backoff curves (initial delay, multiplier) are baked
 * into its internal OkHttpClient / Retrofit transport tier and are non-configurable.
 */
public final class LangChain4jHarness {

    private final OpenAiChatModel model;

    public LangChain4jHarness(String wireMockBaseUrl) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(wireMockBaseUrl)
                .apiKey("demo-key")
                .maxRetries(3)
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    public String execute(String prompt) {
        return model.generate(prompt);
    }
}
