package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.util.Objects;

/**
 * Routes requests to a cheaper or more expensive model based on a
 * complexity score.
 *
 * Scoring is delegated to ComplexityScorer — defaults to
 * HeuristicComplexityScorer but any implementation can be supplied.
 *
 * The router rewrites LlmRequest.model() before passing the request
 * downstream. It does not call the provider itself — that remains
 * the job of LlmClient at the end of the pipeline.
 *
 * Important: this module should sit early in the pipeline, before
 * the cache module, so that cache keys reflect the routed model
 * rather than the originally requested model. If a user explicitly
 * requested a specific model and routing should not override that,
 * place this module after the cache or do not include it at all
 * for that request path.
 */
public final class ModelRouterModule implements PipelineModule {

    private final ComplexityScorer scorer;
    private final RoutingPolicy policy;
    private final RouterEventListener listener;

    public ModelRouterModule(RoutingPolicy policy) {
        this(new HeuristicComplexityScorer(), policy, RouterEventListener.noOp());
    }

    public ModelRouterModule(ComplexityScorer scorer, RoutingPolicy policy) {
        this(scorer, policy, RouterEventListener.noOp());
    }

    public ModelRouterModule(ComplexityScorer scorer,
                             RoutingPolicy policy,
                             RouterEventListener listener) {
        this.scorer = Objects.requireNonNull(scorer);
        this.policy = Objects.requireNonNull(policy);
        this.listener = Objects.requireNonNull(listener);
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {

        double score = scorer.score(request);
        String routedModel = policy.modelFor(score);

        if (routedModel.equals(request.model())) {
            // No change — original model already matches the routed choice
            return next.proceed(request);
        }

        LlmRequest routedRequest = LlmRequest.builder(
                        request.prompt(), routedModel)
                .maxTokens(request.maxTokens())
                .callerId(request.callerId())
                .regionContext(request.regionContext())
                .conversationHistory(request.conversationHistory())
                .build();

        listener.onRouted(request, routedRequest, score);

        return next.proceed(routedRequest);
    }

    @Override
    public String name() { return "model-router"; }
}
