package io.polity4j.reliability.loop;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.AgentLoopException;
import io.polity4j.core.exception.PolityException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * A multi-tenant, thread-safe pipeline module that detects and terminates runaway agentic loops.
 *
 * It uses the session identifier (callerId) to partition tracking states.
 * Four independent trip conditions are checked:
 *
 *   1. Stagnation      - same prompt N times consecutively
 *   2. Frequency       - requests exceeding count within a sliding window
 *   3. Max Cost        - accumulated spend exceeding ceiling
 *   4. Max Iterations  - session exceeding iteration cap
 *
 * Bypasses checks if callerId is null or blank.
 */
public final class AgentLoopDetectorModule implements PipelineModule {

    private final AgentLoopConfig config;
    private final LongSupplier clock;
    private final ConcurrentMap<String, SessionState> states = new ConcurrentHashMap<>();

    public AgentLoopDetectorModule() {
        this(AgentLoopConfig.defaultConfig(), System::currentTimeMillis);
    }

    public AgentLoopDetectorModule(AgentLoopConfig config) {
        this(config, System::currentTimeMillis);
    }

    // Package-private constructor for testing with mock clock
    AgentLoopDetectorModule(AgentLoopConfig config, LongSupplier clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");

        String callerId = request.callerId();

        // If callerId is null or empty, bypass loop detection as per design.
        if (callerId == null || callerId.isBlank()) {
            return next.proceed(request);
        }

        long now = clock.getAsLong();
        SessionState state = states.computeIfAbsent(callerId, k -> new SessionState());

        // 1. Stagnation check — consecutive duplicates
        if (config.maxConsecutiveDuplicates() != null) {
            int consecutive = state.recordPrompt(request.prompt());
            if (consecutive > config.maxConsecutiveDuplicates()) {
                throw new AgentLoopException(
                        String.format("Agent loop detected: caller '%s' submitted duplicate prompt consecutively %d times, exceeding limit of %d",
                                      callerId, consecutive, config.maxConsecutiveDuplicates()),
                        AgentLoopException.TripReason.STAGNATION_DETECTED,
                        state.totalIterations(),
                        state.accumulatedCost()
                );
            }
        }

        // 2. Frequency check — sliding window limit
        if (config.maxRequestsPerSession() != null && config.slidingWindowMs() != null) {
            int count = state.recordRequestTimestamp(now, config.slidingWindowMs());
            if (count > config.maxRequestsPerSession()) {
                throw new AgentLoopException(
                        String.format("Agent loop detected: caller '%s' made %d requests in the last %d ms, exceeding limit of %d",
                                      callerId, count, config.slidingWindowMs(), config.maxRequestsPerSession()),
                        AgentLoopException.TripReason.FREQUENCY_LIMIT_EXCEEDED,
                        state.totalIterations(),
                        state.accumulatedCost()
                );
            }
        }

        // 3. Cost check — accumulated cost limit
        if (config.maxCost() != null) {
            if (state.accumulatedCost().compareTo(config.maxCost()) >= 0) {
                throw new AgentLoopException(
                        String.format("Agent loop detected: caller '%s' accumulated cost is $%s, exceeding limit of $%s",
                                      callerId, state.accumulatedCost(), config.maxCost()),
                        AgentLoopException.TripReason.MAX_COST_EXCEEDED,
                        state.totalIterations(),
                        state.accumulatedCost()
                );
            }
        }

        // 4. Iteration check — hard iteration cap
        int currentIteration = state.incrementIterations();
        if (config.maxIterations() != null && currentIteration > config.maxIterations()) {
            throw new AgentLoopException(
                    String.format("Agent loop detected: caller '%s' total iterations reached %d, exceeding limit of %d",
                                  callerId, currentIteration, config.maxIterations()),
                    AgentLoopException.TripReason.MAX_ITERATIONS_EXCEEDED,
                    currentIteration,
                    state.accumulatedCost()
            );
        }

        // All checks passed — proceed with call
        LlmResponse response = next.proceed(request);

        // Record cost if present
        if (response.estimatedCost() != null) {
            state.addCost(response.estimatedCost());
        }

        return response;
    }

    @Override
    public String name() {
        return "agent-loop-detector";
    }

    // Exposed for testing and manual session state retrieval
    public SessionState session(String callerId) {
        return states.computeIfAbsent(callerId, k -> new SessionState());
    }

    void clear() {
        states.clear();
    }

    public static final class SessionState {
        private final List<Long> requestTimestamps = new ArrayList<>();
        private int totalIterations = 0;
        private BigDecimal accumulatedCost = BigDecimal.ZERO;
        private int consecutiveDuplicates = 0;
        private String lastPrompt = null;

        private SessionState() {}

        synchronized int recordPrompt(String prompt) {
            if (prompt.equals(lastPrompt)) {
                consecutiveDuplicates++;
            } else {
                lastPrompt = prompt;
                consecutiveDuplicates = 1;
            }
            return consecutiveDuplicates;
        }

        synchronized int incrementIterations() {
            totalIterations++;
            return totalIterations;
        }

        synchronized BigDecimal addCost(BigDecimal cost) {
            accumulatedCost = accumulatedCost.add(cost);
            return accumulatedCost;
        }

        public synchronized int totalIterations() {
            return totalIterations;
        }

        public synchronized BigDecimal accumulatedCost() {
            return accumulatedCost;
        }

        synchronized int recordRequestTimestamp(long now, long windowMs) {
            long windowStart = now - windowMs;
            requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
            requestTimestamps.add(now);
            return requestTimestamps.size();
        }

        public synchronized void reset() {
            totalIterations = 0;
            accumulatedCost = BigDecimal.ZERO;
            consecutiveDuplicates = 0;
            lastPrompt = null;
            requestTimestamps.clear();
        }
    }
}
