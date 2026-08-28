package io.polity4j.scratch;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.RateLimitException;
import io.polity4j.reliability.RetryModule;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RetrySpike {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("     RETRY WITH BACKOFF SPIKE BENCHMARK SUMMARY  ");
        System.out.println("=================================================\n");

        runPolity4jSpikeWithRateLimitException();
        runPolity4jSpikeWithRuntimeException();
        runResilience4jSpikeWithRuntimeException();
        runLangChain4jInspection();
    }

    private static void runPolity4jSpikeWithRateLimitException() {
        System.out.println("--- 1. Polity4j Retry (RateLimitException) ---");
        AtomicInteger attempts = new AtomicInteger(0);
        LlmRequest request = LlmRequest.builder("test prompt", "gpt-4o").build();

        io.polity4j.reliability.RetryConfig retryConfig = io.polity4j.reliability.RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(100))
                .multiplier(1.0) // fixed delay 100ms
                .build();
        RetryModule retryModule = new RetryModule(retryConfig);

        PipelineChain operation = req -> {
            int current = attempts.incrementAndGet();
            System.out.println("  [Polity4j Attempt " + current + "]");
            if (current < 3) {
                throw new RateLimitException("openai", 0);
            }
            return LlmResponse.builder("Polity4j Success", "gpt-4o", "openai").build();
        };

        long startTime = System.currentTimeMillis();
        boolean succeeded = false;
        String result = null;
        try {
            LlmResponse response = retryModule.process(request, operation);
            succeeded = true;
            result = response.content();
        } catch (Exception e) {
            result = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("  -> Succeeded: " + succeeded);
        System.out.println("  -> Total Attempts Made: " + attempts.get());
        System.out.println("  -> Final Result: " + result);
        System.out.println("  -> Total Elapsed Time: " + elapsedTime + " ms\n");
    }

    private static void runPolity4jSpikeWithRuntimeException() {
        System.out.println("--- 1b. Polity4j Retry (RuntimeException) ---");
        AtomicInteger attempts = new AtomicInteger(0);
        LlmRequest request = LlmRequest.builder("test prompt", "gpt-4o").build();

        io.polity4j.reliability.RetryConfig retryConfig = io.polity4j.reliability.RetryConfig.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(100))
                .multiplier(1.0)
                .build();
        RetryModule retryModule = new RetryModule(retryConfig);

        PipelineChain operation = req -> {
            int current = attempts.incrementAndGet();
            System.out.println("  [Polity4j Attempt " + current + "]");
            if (current < 3) {
                throw new RuntimeException("Fake transient failure");
            }
            return LlmResponse.builder("Polity4j Success", "gpt-4o", "openai").build();
        };

        long startTime = System.currentTimeMillis();
        boolean succeeded = false;
        String result = null;
        try {
            LlmResponse response = retryModule.process(request, operation);
            succeeded = true;
            result = response.content();
        } catch (Exception e) {
            result = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("  -> Succeeded: " + succeeded);
        System.out.println("  -> Total Attempts Made: " + attempts.get());
        System.out.println("  -> Final Result: " + result);
        System.out.println("  -> Total Elapsed Time: " + elapsedTime + " ms\n");
    }

    private static void runResilience4jSpikeWithRuntimeException() {
        System.out.println("--- 2. Resilience4j Retry (RuntimeException) ---");
        AtomicInteger attempts = new AtomicInteger(0);

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(e -> true)
                .build();

        Retry retry = Retry.of("benchmarkRetry", config);

        Supplier<String> fakeOperation = () -> {
            int current = attempts.incrementAndGet();
            System.out.println("  [Resilience4j Attempt " + current + "]");
            if (current < 3) {
                throw new RuntimeException("Fake transient failure");
            }
            return "Resilience4j Success";
        };

        Supplier<String> decorated = Retry.decorateSupplier(retry, fakeOperation);

        long startTime = System.currentTimeMillis();
        boolean succeeded = false;
        String result = null;
        try {
            result = decorated.get();
            succeeded = true;
        } catch (Exception e) {
            result = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("  -> Succeeded: " + succeeded);
        System.out.println("  -> Total Attempts Made: " + attempts.get());
        System.out.println("  -> Final Result: " + result);
        System.out.println("  -> Total Elapsed Time: " + elapsedTime + " ms\n");
    }

    private static void runLangChain4jInspection() {
        System.out.println("--- 3. LangChain4j Inspection ---");
        System.out.println("  Inspecting LangChain4j OpenAiChatModel API...");

        // Demonstrate builder options on OpenAiChatModel
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey("demo-key")
                    .maxRetries(3)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            System.out.println("  -> OpenAiChatModel constructed with maxRetries=3.");
            System.out.println("  -> Retries in LangChain4j are internal to HTTP client (OkHttpClient / Retrofit).");
            System.out.println("  -> NO standalone Retry interface/wrapper exposed for arbitrary Suppliers/Callables.");
            System.out.println("  -> NO knobs for initialDelay, backoff multiplier, jitter, or custom retry predicates.");
        } catch (Exception e) {
            System.out.println("  -> Error instantiating model: " + e.getMessage());
        }
        System.out.println();
    }
}
