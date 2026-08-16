package io.polity4j.examples.full;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demonstrates non-blocking high-throughput pipeline execution
 * using Virtual Threads / Executor Pools and CompletableFuture wrapping.
 */
public class AsyncIntegrationDemo {

    public static void main(String[] args) {
        LlmClient client = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return LlmResponse.builder("Async answer for: " + request.prompt(), request.model(), provider())
                        .inputTokens(15)
                        .outputTokens(20)
                        .estimatedCost(new BigDecimal("0.00005"))
                        .build();
            }

            @Override
            public String provider() {
                return "async-simulated-provider";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(client).build();

        System.out.println("=".repeat(60));
        System.out.println("Polity4j Asynchronous & Non-Blocking Execution Demo");
        System.out.println("=".repeat(60));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int totalRequests = 20;

        List<CompletableFuture<LlmResponse>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalRequests; i++) {
            final int id = i;
            LlmRequest req = LlmRequest.builder("Async task #" + id, "gpt-4o").build();

            // Non-blocking submission
            CompletableFuture<LlmResponse> future = CompletableFuture.supplyAsync(
                    () -> pipeline.execute(req),
                    executor
            );
            futures.add(future);
        }

        System.out.println("Submitted " + totalRequests + " requests asynchronously without blocking main thread.");

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("-".repeat(60));
        System.out.println("All " + totalRequests + " requests completed in " + duration + "ms.");
        System.out.println("Sample Response #1: " + futures.get(0).join().content());
        System.out.println("Sample Response #20: " + futures.get(totalRequests - 1).join().content());
        System.out.println("=".repeat(60));

        executor.shutdown();
    }
}
