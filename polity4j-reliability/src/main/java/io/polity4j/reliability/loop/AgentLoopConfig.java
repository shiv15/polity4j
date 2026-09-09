package io.polity4j.reliability.loop;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Configuration for {@link AgentLoopDetectorModule}.
 * Holds the parameters needed to identify runaway or recursive agent loops.
 */
public final class AgentLoopConfig {
    public static final AgentLoopConfig DEFAULT = builder().build();

    private final Integer maxRequestsPerSession;
    private final Long slidingWindowMs;
    private final Integer maxConsecutiveDuplicates;
    private final Integer stagnationLimit;
    private final Integer maxIterations;
    private final BigDecimal maxCost;
    private final boolean resultAwareStagnation;

    private AgentLoopConfig(Builder builder) {
        this.maxRequestsPerSession = builder.maxRequestsPerSession;
        this.slidingWindowMs = builder.slidingWindowMs;
        this.maxConsecutiveDuplicates = builder.maxConsecutiveDuplicates;
        this.stagnationLimit = builder.stagnationLimit;
        this.maxIterations = builder.maxIterations;
        this.maxCost = builder.maxCost;
        this.resultAwareStagnation = builder.resultAwareStagnation;
    }

    public Integer maxRequestsPerSession() {
        return maxRequestsPerSession;
    }

    public Long slidingWindowMs() {
        return slidingWindowMs;
    }

    public Integer maxConsecutiveDuplicates() {
        return maxConsecutiveDuplicates;
    }

    public Integer stagnationLimit() {
        return stagnationLimit;
    }

    public Integer maxIterations() {
        return maxIterations;
    }

    public BigDecimal maxCost() {
        return maxCost;
    }

    public boolean resultAwareStagnation() {
        return resultAwareStagnation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentLoopConfig defaultConfig() {
        return new Builder().build();
    }

    public static final class Builder {
        private Integer maxRequestsPerSession = 10;
        private Long slidingWindowMs = 60_000L; // 1 minute
        private Integer maxConsecutiveDuplicates = 3;
        private Integer stagnationLimit = 4;
        private Integer maxIterations = 10;
        private BigDecimal maxCost = null;
        private boolean resultAwareStagnation = true;

        public Builder maxRequestsPerSession(Integer maxRequestsPerSession) {
            if (maxRequestsPerSession != null && maxRequestsPerSession <= 0) {
                throw new IllegalArgumentException("maxRequestsPerSession must be greater than 0");
            }
            this.maxRequestsPerSession = maxRequestsPerSession;
            return this;
        }

        public Builder slidingWindowMs(Long slidingWindowMs) {
            if (slidingWindowMs != null && slidingWindowMs <= 0) {
                throw new IllegalArgumentException("slidingWindowMs must be greater than 0");
            }
            this.slidingWindowMs = slidingWindowMs;
            return this;
        }

        public Builder maxConsecutiveDuplicates(Integer maxConsecutiveDuplicates) {
            if (maxConsecutiveDuplicates != null && maxConsecutiveDuplicates <= 0) {
                throw new IllegalArgumentException("maxConsecutiveDuplicates must be greater than 0");
            }
            this.maxConsecutiveDuplicates = maxConsecutiveDuplicates;
            if (maxConsecutiveDuplicates != null) {
                this.stagnationLimit = maxConsecutiveDuplicates + 1;
            }
            return this;
        }

        public Builder stagnationLimit(int stagnationLimit) {
            if (stagnationLimit < 2) {
                throw new IllegalArgumentException("stagnationLimit must be at least 2");
            }
            this.stagnationLimit = stagnationLimit;
            this.maxConsecutiveDuplicates = stagnationLimit - 1;
            return this;
        }

        public Builder resultAwareStagnation(boolean resultAwareStagnation) {
            this.resultAwareStagnation = resultAwareStagnation;
            return this;
        }

        public Builder maxIterations(Integer maxIterations) {
            if (maxIterations != null && maxIterations < 1) {
                throw new IllegalArgumentException("maxIterations must be at least 1");
            }
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder maxCost(BigDecimal maxCost) {
            if (maxCost != null && maxCost.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("maxCost must be positive");
            }
            this.maxCost = maxCost;
            return this;
        }

        public Builder maxCost(String maxCost) {
            if (maxCost == null) {
                this.maxCost = null;
                return this;
            }
            return maxCost(new BigDecimal(maxCost));
        }

        public AgentLoopConfig build() {
            if (maxRequestsPerSession == null
                    && maxConsecutiveDuplicates == null
                    && stagnationLimit == null
                    && maxIterations == null
                    && maxCost == null) {
                throw new IllegalStateException("at least one trip condition must be configured");
            }
            return new AgentLoopConfig(this);
        }
    }
}
