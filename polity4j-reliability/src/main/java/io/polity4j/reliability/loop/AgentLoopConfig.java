package io.polity4j.reliability.loop;

/**
 * Configuration for {@link AgentLoopDetectorModule}.
 * Holds the parameters needed to identify runaway or recursive agent loops.
 */
public final class AgentLoopConfig {
    private final int maxRequestsPerSession;
    private final int maxConsecutiveDuplicates;
    private final long slidingWindowMs;

    public AgentLoopConfig(int maxRequestsPerSession, int maxConsecutiveDuplicates, long slidingWindowMs) {
        if (maxRequestsPerSession <= 0) {
            throw new IllegalArgumentException("maxRequestsPerSession must be greater than 0");
        }
        if (maxConsecutiveDuplicates <= 0) {
            throw new IllegalArgumentException("maxConsecutiveDuplicates must be greater than 0");
        }
        if (slidingWindowMs <= 0) {
            throw new IllegalArgumentException("slidingWindowMs must be greater than 0");
        }
        this.maxRequestsPerSession = maxRequestsPerSession;
        this.maxConsecutiveDuplicates = maxConsecutiveDuplicates;
        this.slidingWindowMs = slidingWindowMs;
    }

    public int maxRequestsPerSession() {
        return maxRequestsPerSession;
    }

    public int maxConsecutiveDuplicates() {
        return maxConsecutiveDuplicates;
    }

    public long slidingWindowMs() {
        return slidingWindowMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentLoopConfig defaultConfig() {
        return new Builder().build();
    }

    public static final class Builder {
        private int maxRequestsPerSession = 10;
        private int maxConsecutiveDuplicates = 3;
        private long slidingWindowMs = 60_000; // 1 minute

        public Builder maxRequestsPerSession(int maxRequestsPerSession) {
            this.maxRequestsPerSession = maxRequestsPerSession;
            return this;
        }

        public Builder maxConsecutiveDuplicates(int maxConsecutiveDuplicates) {
            this.maxConsecutiveDuplicates = maxConsecutiveDuplicates;
            return this;
        }

        public Builder slidingWindowMs(long slidingWindowMs) {
            this.slidingWindowMs = slidingWindowMs;
            return this;
        }

        public AgentLoopConfig build() {
            return new AgentLoopConfig(maxRequestsPerSession, maxConsecutiveDuplicates, slidingWindowMs);
        }
    }
}
