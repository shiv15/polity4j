package io.polity4j.reliability.loop;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.AgentLoopException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopDetectorModuleTest {

    private static final String MODEL = "gpt-4o";

    private static LlmResponse okResponse() {
        return LlmResponse.builder("ok", MODEL, "openai").build();
    }

    private static LlmResponse okResponse(String cost) {
        return LlmResponse.builder("tool result", MODEL, "openai")
                .estimatedCost(new BigDecimal(cost))
                .build();
    }

    private static final PipelineChain OK_CHAIN = request -> okResponse();

    private static PipelineChain successChain(String cost) {
        return req -> okResponse(cost);
    }

    private static LlmRequest request(String prompt) {
        return LlmRequest.builder(prompt, MODEL)
                .callerId("agent-session-1")
                .build();
    }

    // ==================================================================
    // Original Multi-tenant / Sliding Window Tests
    // ==================================================================

    @Test
    void testConfigurationValidation() {
        assertThatThrownBy(() -> AgentLoopConfig.builder().maxRequestsPerSession(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentLoopConfig.builder().maxConsecutiveDuplicates(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentLoopConfig.builder().slidingWindowMs(0L).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AgentLoopDetectorModule(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNormalRequestsPass() {
        var detector = new AgentLoopDetectorModule();
        var request = LlmRequest.builder("test prompt", MODEL)
                .callerId("session-1")
                .build();

        var response = detector.process(request, OK_CHAIN);
        assertThat(response.content()).isEqualTo("ok");
    }

    @Test
    void testBypassIfCallerIdMissing() {
        var config = AgentLoopConfig.builder()
                .maxRequestsPerSession(1)
                .maxConsecutiveDuplicates(1)
                .build();
        var detector = new AgentLoopDetectorModule(config);

        // First request with no callerId
        var req1 = LlmRequest.builder("prompt", MODEL).build(); // callerId is null
        detector.process(req1, OK_CHAIN);
        // Second identical request with no callerId shouldn't throw loop exception
        detector.process(req1, OK_CHAIN);
        detector.process(req1, OK_CHAIN);
    }

    @Test
    void testConsecutiveDuplicatesBreach() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(2)
                .build();
        var detector = new AgentLoopDetectorModule(config);
        var req = LlmRequest.builder("dup prompt", MODEL).callerId("user-1").build();

        // 1st request
        detector.process(req, OK_CHAIN);
        // 2nd request
        detector.process(req, OK_CHAIN);
        // 3rd request (consecutive duplicate limit is 2, so 3rd consecutive duplicate should throw)
        assertThatThrownBy(() -> detector.process(req, OK_CHAIN))
                .isInstanceOf(AgentLoopException.class)
                .hasMessageContaining("duplicate prompt consecutively")
                .hasMessageContaining("user-1");
    }

    @Test
    void testDifferentPromptResetsConsecutiveDuplicates() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(2)
                .build();
        var detector = new AgentLoopDetectorModule(config);
        var req1 = LlmRequest.builder("dup prompt", MODEL).callerId("user-1").build();
        var req2 = LlmRequest.builder("different prompt", MODEL).callerId("user-1").build();

        detector.process(req1, OK_CHAIN);
        detector.process(req1, OK_CHAIN);
        // A different request resets consecutive counter
        detector.process(req2, OK_CHAIN);
        // Now another req1 is fine
        detector.process(req1, OK_CHAIN);
        detector.process(req1, OK_CHAIN);
        // Next req1 should throw
        assertThatThrownBy(() -> detector.process(req1, OK_CHAIN))
                .isInstanceOf(AgentLoopException.class);
    }

    @Test
    void testFrequencyBreachWithinSlidingWindow() {
        var clockTime = new AtomicLong(1000);
        var config = AgentLoopConfig.builder()
                .maxRequestsPerSession(3)
                .slidingWindowMs(1000L)
                .build();
        var detector = new AgentLoopDetectorModule(config, clockTime::get);

        var req1 = LlmRequest.builder("p1", MODEL).callerId("user-1").build();
        var req2 = LlmRequest.builder("p2", MODEL).callerId("user-1").build();
        var req3 = LlmRequest.builder("p3", MODEL).callerId("user-1").build();
        var req4 = LlmRequest.builder("p4", MODEL).callerId("user-1").build();

        // Make 3 requests at t = 1000. Under threshold.
        detector.process(req1, OK_CHAIN);
        detector.process(req2, OK_CHAIN);
        detector.process(req3, OK_CHAIN);

        // 4th request at t = 1500 (still in the 1000ms window from t=1000) should throw.
        clockTime.set(1500);
        assertThatThrownBy(() -> detector.process(req4, OK_CHAIN))
                .isInstanceOf(AgentLoopException.class)
                .hasMessageContaining("exceeding limit of 3");
    }

    @Test
    void testSlidingWindowSlidesCorrectly() {
        var clockTime = new AtomicLong(1000);
        var config = AgentLoopConfig.builder()
                .maxRequestsPerSession(3)
                .slidingWindowMs(1000L)
                .build();
        var detector = new AgentLoopDetectorModule(config, clockTime::get);

        var req1 = LlmRequest.builder("p1", MODEL).callerId("user-1").build();
        var req2 = LlmRequest.builder("p2", MODEL).callerId("user-1").build();
        var req3 = LlmRequest.builder("p3", MODEL).callerId("user-1").build();
        var req4 = LlmRequest.builder("p4", MODEL).callerId("user-1").build();

        // 3 requests at t = 1000
        detector.process(req1, OK_CHAIN);
        detector.process(req2, OK_CHAIN);
        detector.process(req3, OK_CHAIN);

        // Advance clock past the 1000ms window (e.g. to 2100)
        // Previous 3 requests should be cleared/out of window
        clockTime.set(2100);
        detector.process(req4, OK_CHAIN); // should succeed
    }

    @Test
    void testConcurrentAccessIsThreadSafe() throws InterruptedException {
        var config = AgentLoopConfig.builder()
                .maxRequestsPerSession(1000)
                .maxConsecutiveDuplicates(1000)
                .maxIterations(1000)
                .build();
        var detector = new AgentLoopDetectorModule(config);

        int numThreads = 10;
        int reqsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulRequests = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final String callerId = "user-" + i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < reqsPerThread; j++) {
                        var req = LlmRequest.builder("p-" + j, MODEL)
                                .callerId(callerId)
                                .build();
                        detector.process(req, OK_CHAIN);
                        successfulRequests.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertThat(successfulRequests.get()).isEqualTo(numThreads * reqsPerThread);
    }

    // ==================================================================
    // Cost, Iterations, and Stagnation Limit Tests
    // ==================================================================

    @Test
    void allowsCallsUpToMaxIterations() {
        var config = AgentLoopConfig.builder()
                .maxIterations(3)
                .build();
        var module = new AgentLoopDetectorModule(config);

        for (int i = 0; i < 3; i++) {
            var response = module.process(
                    request("prompt " + i), successChain("0.01"));
            assertThat(response.content()).isEqualTo("tool result");
        }
    }

    @Test
    void tripsOnMaxIterationsExceeded() {
        var config = AgentLoopConfig.builder()
                .maxIterations(3)
                .build();
        var module = new AgentLoopDetectorModule(config);

        for (int i = 0; i < 3; i++) {
            module.process(request("prompt " + i), successChain("0.01"));
        }

        assertThatThrownBy(() ->
                module.process(request("prompt 4"), successChain("0.01")))
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.MAX_ITERATIONS_EXCEEDED);
    }

    @Test
    void iterationCountIsCorrectWhenTripping() {
        var config = AgentLoopConfig.builder()
                .maxIterations(2)
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("p1"), successChain("0.01"));
        module.process(request("p2"), successChain("0.01"));

        var ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(
                        () -> module.process(request("p3"), successChain("0.01")),
                        AgentLoopException.class);

        assertThat(ex.iterations()).isEqualTo(3);
    }

    @Test
    void allowsCallsUnderMaxCost() {
        var config = AgentLoopConfig.builder()
                .maxCost("1.00")
                .build();
        var module = new AgentLoopDetectorModule(config);

        // Three calls at $0.25 each = $0.75 total, under $1.00
        for (int i = 0; i < 3; i++) {
            var response = module.process(
                    request("prompt " + i), successChain("0.25"));
            assertThat(response.content()).isEqualTo("tool result");
        }
    }

    @Test
    void tripsWhenAccumulatedCostReachesCeiling() {
        var config = AgentLoopConfig.builder()
                .maxCost("0.50")
                .build();
        var module = new AgentLoopDetectorModule(config);

        // First call costs $0.50 — recorded after success
        module.process(request("p1"), successChain("0.50"));

        // Second call — accumulated cost is now $0.50, at ceiling
        assertThatThrownBy(() ->
                module.process(request("p2"), successChain("0.25")))
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.MAX_COST_EXCEEDED);
    }

    @Test
    void accumulatedCostIsCorrectWhenTripping() {
        var config = AgentLoopConfig.builder()
                .maxCost("0.50")
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("p1"), successChain("0.30"));
        module.process(request("p2"), successChain("0.30"));

        var ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(
                        () -> module.process(request("p3"), successChain("0.30")),
                        AgentLoopException.class);

        assertThat(ex.accumulatedCost())
                .isEqualByComparingTo("0.60");
    }

    @Test
    void allowsDifferentPromptsWithoutStagnation() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(2) // equivalent to stagnationLimit(3)
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("prompt A"), successChain("0.01"));
        module.process(request("prompt B"), successChain("0.01"));
        module.process(request("prompt A"), successChain("0.01")); // not consecutive
        module.process(request("prompt C"), successChain("0.01"));
        // No exception — never 3 consecutive identical prompts
    }

    @Test
    void tripsOnConsecutiveIdenticalPromptsBlueprint() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(2) // equivalent to stagnationLimit(3)
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("same prompt"), successChain("0.01"));
        module.process(request("same prompt"), successChain("0.01"));

        assertThatThrownBy(() ->
                module.process(request("same prompt"), successChain("0.01")))
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.STAGNATION_DETECTED);
    }

    @Test
    void consecutiveCountResetsOnDifferentPromptBlueprint() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(2)
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("same"), successChain("0.01"));
        module.process(request("same"), successChain("0.01"));
        // Different prompt resets the count
        module.process(request("different"), successChain("0.01"));
        // Back to same — count starts at 1 again, no trip
        module.process(request("same"), successChain("0.01"));
        module.process(request("same"), successChain("0.01"));
        // Still only 2 consecutive duplicates — no trip yet
    }

    @Test
    void sessionResetAllowsFreshStart() {
        var config = AgentLoopConfig.builder()
                .maxIterations(2)
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("p1"), successChain("0.01"));
        module.process(request("p2"), successChain("0.01"));

        // Reset between agent runs
        module.session("agent-session-1").reset();

        // Should allow calls again
        var response = module.process(request("p3"), successChain("0.01"));
        assertThat(response.content()).isEqualTo("tool result");
    }

    @Test
    void stagnationTripsBeforeCostWhenBothConfigured() {
        var config = AgentLoopConfig.builder()
                .maxConsecutiveDuplicates(1) // stagnation limit 2
                .maxCost("100.00")
                .build();
        var module = new AgentLoopDetectorModule(config);

        module.process(request("same"), successChain("0.01"));

        assertThatThrownBy(() ->
                module.process(request("same"), successChain("0.01")))
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.STAGNATION_DETECTED);
    }

    @Test
    void nameIsCorrect() {
        var module = new AgentLoopDetectorModule(AgentLoopConfig.DEFAULT);
        assertThat(module.name()).isEqualTo("agent-loop-detector");
    }
}
