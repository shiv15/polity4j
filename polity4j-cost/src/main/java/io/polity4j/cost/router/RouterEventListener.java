package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;

/**
 * Observes routing decisions — useful for logging, metrics, and
 * the SaaS dashboard showing model downgrade savings.
 */
public interface RouterEventListener {

    /**
     * Fired whenever the router changes the model on a request.
     *
     * @param original      the incoming request before routing
     * @param routed        the request after routing, with model changed
     * @param complexityScore the score that drove the decision
     */
    void onRouted(LlmRequest original,
                  LlmRequest routed,
                  double complexityScore);

    static RouterEventListener noOp() {
        return (original, routed, complexityScore) -> {};
    }
}
