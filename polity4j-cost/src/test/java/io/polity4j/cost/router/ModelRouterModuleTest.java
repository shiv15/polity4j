package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRouterModuleTest {

    private static final String CHEAP = "claude-3-haiku-20240307";
    private static final String EXPENSIVE = "claude-3-5-sonnet-20241022";

    // Scorer that always returns a fixed score — for deterministic testing
    private static ComplexityScorer fixedScorer(double score) {
        return request -> score;
    }

    private static PipelineChain echoModelChain() {
        return request -> LlmResponse
                .builder("model-used:" + request.model(), request.model(), "anthropic")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    private static RoutingPolicy policy() {
        return RoutingPolicy.builder()
                .threshold(0.5)
                .cheapModel(CHEAP)
                .expensiveModel(EXPENSIVE)
                .build();
    }

    // ------------------------------------------------------------------
    // Routing decisions
    // ------------------------------------------------------------------

    @Test
    void lowComplexityRoutesToCheapModel() {
        var module = new ModelRouterModule(fixedScorer(0.2), policy());
        var request = LlmRequest.builder("simple question", "gpt-4o").build();

        var response = module.process(request, echoModelChain());

        assertThat(response.content()).isEqualTo("model-used:" + CHEAP);
    }

    @Test
    void highComplexityRoutesToExpensiveModel() {
        var module = new ModelRouterModule(fixedScorer(0.9), policy());
        var request = LlmRequest.builder("complex question", "gpt-4o").build();

        var response = module.process(request, echoModelChain());

        assertThat(response.content()).isEqualTo("model-used:" + EXPENSIVE);
    }

    @Test
    void scoreExactlyAtThresholdRoutesToExpensive() {
        var module = new ModelRouterModule(fixedScorer(0.5), policy());
        var request = LlmRequest.builder("borderline", "gpt-4o").build();

        var response = module.process(request, echoModelChain());

        // threshold is inclusive on the expensive side
        assertThat(response.content()).isEqualTo("model-used:" + EXPENSIVE);
    }

    // ------------------------------------------------------------------
    // Request preservation
    // ------------------------------------------------------------------

    @Test
    void preservesPromptAndOtherFieldsWhenRouting() {
        var module = new ModelRouterModule(fixedScorer(0.9), policy());
        var request = LlmRequest.builder("my exact prompt", "gpt-4o")
                .maxTokens(500)
                .callerId("service-a")
                .regionContext("eu-west-1")
                .build();

        PipelineChain capturingChain = req -> {
            assertThat(req.prompt()).isEqualTo("my exact prompt");
            assertThat(req.maxTokens()).isEqualTo(500);
            assertThat(req.callerId()).isEqualTo("service-a");
            assertThat(req.regionContext()).isEqualTo("eu-west-1");
            assertThat(req.model()).isEqualTo(EXPENSIVE);
            return LlmResponse.builder("ok", req.model(), "anthropic").build();
        };

        module.process(request, capturingChain);
    }

    @Test
    void skipsRewriteWhenModelAlreadyMatchesRoutedChoice() {
        var module = new ModelRouterModule(fixedScorer(0.9), policy());
        // Request already specifies the expensive model
        var request = LlmRequest.builder("complex", EXPENSIVE).build();

        PipelineChain identityCheckingChain = req -> {
            // Should be the exact same request object reference behavior —
            // not rewritten unnecessarily
            assertThat(req.model()).isEqualTo(EXPENSIVE);
            return LlmResponse.builder("ok", req.model(), "anthropic").build();
        };

        module.process(request, identityCheckingChain);
    }

    // ------------------------------------------------------------------
    // Event listener
    // ------------------------------------------------------------------

    @Test
    void firesListenerWhenRoutingOccurs() {
        var routedScores = new ArrayList<Double>();
        RouterEventListener listener = (original, routed, score) ->
                routedScores.add(score);

        var module = new ModelRouterModule(
                fixedScorer(0.8), policy(), listener);
        var request = LlmRequest.builder("complex", "gpt-4o").build();

        module.process(request, echoModelChain());

        assertThat(routedScores).containsExactly(0.8);
    }

    @Test
    void doesNotFireListenerWhenNoRoutingChangeOccurs() {
        var fired = new ArrayList<Double>();
        RouterEventListener listener = (original, routed, score) ->
                fired.add(score);

        var module = new ModelRouterModule(
                fixedScorer(0.9), policy(), listener);
        // Already the expensive model — no change
        var request = LlmRequest.builder("complex", EXPENSIVE).build();

        module.process(request, echoModelChain());

        assertThat(fired).isEmpty();
    }

    // ------------------------------------------------------------------
    // Default scorer wiring
    // ------------------------------------------------------------------

    @Test
    void usesHeuristicScorerByDefault() {
        // Constructing with just a policy should use HeuristicComplexityScorer
        var module = new ModelRouterModule(policy());
        var simpleRequest = LlmRequest.builder("hi", "gpt-4o").build();

        var response = module.process(simpleRequest, echoModelChain());

        // Simple short prompt should score low and route to cheap model
        assertThat(response.content()).isEqualTo("model-used:" + CHEAP);
    }

    @Test
    void nameIsCorrect() {
        var module = new ModelRouterModule(policy());
        assertThat(module.name()).isEqualTo("model-router");
    }
}
