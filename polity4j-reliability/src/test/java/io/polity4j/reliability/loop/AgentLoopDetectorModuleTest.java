package io.polity4j.reliability.loop;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.AgentLoopException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopDetectorModuleTest {

    private static LlmResponse okResponse() {
        return LlmResponse.builder("ok", "gpt-4o", "openai").build();
    }

    private static final PipelineChain OK_CHAIN = request -> okResponse();

    @Test
    void testConfigurationValidation() {
        assertThatThrownBy(() -> AgentLoopConfig.builder().maxRequestsPerSession(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentLoopConfig.builder().maxConsecutiveDuplicates(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentLoopConfig.builder().slidingWindowMs(0).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AgentLoopDetectorModule(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNormalRequestsPass() {
        var detector = new AgentLoopDetectorModule();
        var request = LlmRequest.builder("test prompt", "gpt-4o")
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
        var req1 = LlmRequest.builder("prompt", "gpt-4o").build(); // callerId is null
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
        var req = LlmRequest.builder("dup prompt", "gpt-4o").callerId("user-1").build();

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
        var req1 = LlmRequest.builder("dup prompt", "gpt-4o").callerId("user-1").build();
        var req2 = LlmRequest.builder("different prompt", "gpt-4o").callerId("user-1").build();

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
                .slidingWindowMs(1000)
                .build();
        var detector = new AgentLoopDetectorModule(config, clockTime::get);

        var req1 = LlmRequest.builder("p1", "gpt-4o").callerId("user-1").build();
        var req2 = LlmRequest.builder("p2", "gpt-4o").callerId("user-1").build();
        var req3 = LlmRequest.builder("p3", "gpt-4o").callerId("user-1").build();
        var req4 = LlmRequest.builder("p4", "gpt-4o").callerId("user-1").build();

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
                .slidingWindowMs(1000)
                .build();
        var detector = new AgentLoopDetectorModule(config, clockTime::get);

        var req1 = LlmRequest.builder("p1", "gpt-4o").callerId("user-1").build();
        var req2 = LlmRequest.builder("p2", "gpt-4o").callerId("user-1").build();
        var req3 = LlmRequest.builder("p3", "gpt-4o").callerId("user-1").build();
        var req4 = LlmRequest.builder("p4", "gpt-4o").callerId("user-1").build();

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
                        var req = LlmRequest.builder("p-" + j, "gpt-4o")
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
}
