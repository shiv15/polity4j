package io.polity4j.core;

import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadExecutionTest {

    private static LlmClient stubClient() {
        return new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return LlmResponse.builder("Echo: " + request.prompt(), request.model(), "stub")
                        .inputTokens(10)
                        .outputTokens(20)
                        .build();
            }

            @Override
            public String provider() {
                return "stub";
            }
        };
    }

    @Test
    void executeHighConcurrencyNonBlockingTasks() {
        LlmPipeline pipeline = LlmPipeline.builder(stubClient()).build();
        int taskCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<CompletableFuture<LlmResponse>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                final int idx = i;
                CompletableFuture<LlmResponse> future = CompletableFuture.supplyAsync(
                        () -> pipeline.execute(LlmRequest.builder("Prompt #" + idx, "gpt-4o").build()),
                        executor
                );
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (int i = 0; i < taskCount; i++) {
                LlmResponse response = futures.get(i).join();
                assertThat(response.content()).isEqualTo("Echo: Prompt #" + i);
                assertThat(response.provider()).isEqualTo("stub");
            }
        } finally {
            executor.shutdown();
        }
    }
}
