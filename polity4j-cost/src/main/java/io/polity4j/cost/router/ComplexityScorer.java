package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;

/**
 * Scores the complexity of a request on a scale of 0.0 (trivial)
 * to 1.0 (highly complex).
 *
 * The router uses this score to decide whether to downgrade the
 * request to a cheaper model.
 *
 * The default implementation (HeuristicComplexityScorer) uses simple
 * signals — prompt length, code block presence, conversation depth —
 * with zero external dependencies. Users with more accurate needs
 * can implement this interface with an embedding-based or ML-based
 * scorer without changing anything else in the router.
 */
public interface ComplexityScorer {

    /**
     * @return a score between 0.0 and 1.0, inclusive
     */
    double score(LlmRequest request);
}
