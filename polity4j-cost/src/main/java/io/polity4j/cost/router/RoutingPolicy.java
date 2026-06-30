package io.polity4j.cost.router;

import java.util.Objects;

/**
 * Maps a complexity score to a model choice.
 *
 * Below threshold  → cheapModel
 * At or above      → expensiveModel
 *
 * Example:
 *   RoutingPolicy.builder()
 *       .threshold(0.5)
 *       .cheapModel("claude-3-haiku-20240307")
 *       .expensiveModel("claude-3-5-sonnet-20241022")
 *       .build();
 */
public final class RoutingPolicy {

    private final double threshold;
    private final String cheapModel;
    private final String expensiveModel;

    private RoutingPolicy(Builder builder) {
        this.threshold = builder.threshold;
        this.cheapModel = builder.cheapModel;
        this.expensiveModel = builder.expensiveModel;
    }

    public double threshold() { return threshold; }
    public String cheapModel() { return cheapModel; }
    public String expensiveModel() { return expensiveModel; }

    public String modelFor(double score) {
        return score >= threshold ? expensiveModel : cheapModel;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private double threshold = 0.5;
        private String cheapModel;
        private String expensiveModel;

        public Builder threshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0)
                throw new IllegalArgumentException(
                        "threshold must be between 0.0 and 1.0");
            this.threshold = threshold;
            return this;
        }

        public Builder cheapModel(String cheapModel) {
            this.cheapModel = Objects.requireNonNull(cheapModel);
            return this;
        }

        public Builder expensiveModel(String expensiveModel) {
            this.expensiveModel = Objects.requireNonNull(expensiveModel);
            return this;
        }

        public RoutingPolicy build() {
            Objects.requireNonNull(cheapModel, "cheapModel must be set");
            Objects.requireNonNull(expensiveModel, "expensiveModel must be set");
            return new RoutingPolicy(this);
        }
    }
}
