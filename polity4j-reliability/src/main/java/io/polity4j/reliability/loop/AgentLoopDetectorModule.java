package io.polity4j.reliability.loop;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.AgentLoopException;
import io.polity4j.core.exception.PolityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * A pipeline module that monitors LLM requests to detect runaway agent execution loops.
 * It uses the session identifier (callerId) to track:
 * <ul>
 *   <li>Overall request frequency within a configurable sliding window.</li>
 *   <li>Consecutive identical prompt count.</li>
 * </ul>
 * If any threshold is breached, it throws an {@link AgentLoopException}.
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
        String callerId = request.callerId();

        // If callerId is null or empty, bypass loop detection as per approved plan.
        if (callerId == null || callerId.isBlank()) {
            return next.proceed(request);
        }

        long now = clock.getAsLong();
        SessionState state = states.computeIfAbsent(callerId, k -> new SessionState());
        state.recordRequest(request.prompt(), now, config, callerId);

        return next.proceed(request);
    }

    @Override
    public String name() {
        return "agent-loop-detector";
    }

    // Package-private method to allow tests to clear state
    void clear() {
        states.clear();
    }

    private static final class SessionState {
        private final List<Long> requestTimestamps = new ArrayList<>();
        private String lastPrompt = null;
        private int consecutiveDuplicates = 0;

        synchronized void recordRequest(String prompt, long now, AgentLoopConfig config, String callerId) {
            // Remove timestamps outside of sliding window
            long windowStart = now - config.slidingWindowMs();
            requestTimestamps.removeIf(timestamp -> timestamp < windowStart);
            requestTimestamps.add(now);

            if (requestTimestamps.size() > config.maxRequestsPerSession()) {
                throw new AgentLoopException(String.format(
                        "Agent loop detected: caller '%s' made %d requests in the last %d ms, exceeding limit of %d",
                        callerId, requestTimestamps.size(), config.slidingWindowMs(), config.maxRequestsPerSession()
                ));
            }

            if (prompt.equals(lastPrompt)) {
                consecutiveDuplicates++;
            } else {
                lastPrompt = prompt;
                consecutiveDuplicates = 1;
            }

            if (consecutiveDuplicates > config.maxConsecutiveDuplicates()) {
                throw new AgentLoopException(String.format(
                        "Agent loop detected: caller '%s' submitted duplicate prompt consecutively %d times, exceeding limit of %d",
                        callerId, consecutiveDuplicates, config.maxConsecutiveDuplicates()
                ));
            }
        }
    }
}
