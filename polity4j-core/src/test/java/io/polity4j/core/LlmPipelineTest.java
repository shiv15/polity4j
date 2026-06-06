package io.polity4j.core;

import io.polity4j.core.exception.BudgetExceededException;
import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmPipelineTest {

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Client that always returns a fixed response */
    static LlmClient stubClient(String responseContent) {
        return new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                return LlmResponse.builder(responseContent, request.model(), "stub")
                        .inputTokens(10)
                        .outputTokens(20)
                        .latencyMs(50)
                        .build();
            }

            @Override
            public String provider() { return "stub"; }
        };
    }

    /** Module that records the order it was called in */
    static PipelineModule orderRecordingModule(String name, List<String> callOrder) {
        return new PipelineModule() {
            @Override
            public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
                callOrder.add(name);
                return next.proceed(request);
            }

            @Override
            public String name() { return name; }
        };
    }

    /** Module that modifies the request before passing it on */
    static PipelineModule requestMutatingModule(String newPrompt) {
        return new PipelineModule() {
            @Override
            public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
                LlmRequest modified = LlmRequest.builder(newPrompt, request.model())
                        .callerId(request.callerId())
                        .regionContext(request.regionContext())
                        .build();
                return next.proceed(modified);
            }

            @Override
            public String name() { return "request-mutator"; }
        };
    }

    /** Module that short-circuits and returns without calling next */
    static PipelineModule shortCircuitModule(String cachedContent) {
        return new PipelineModule() {
            @Override
            public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
                return LlmResponse.builder(cachedContent, request.model(), "cache")
                        .build();
            }

            @Override
            public String name() { return "short-circuit"; }
        };
    }

    /** Module that throws to abort the pipeline */
    static PipelineModule abortingModule() {
        return new PipelineModule() {
            @Override
            public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
                throw new BudgetExceededException(
                        new BigDecimal("10.00"),
                        new BigDecimal("12.50"));
            }

            @Override
            public String name() { return "aborting"; }
        };
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void noModulesCallsClientDirectly() throws PolityException {
        var pipeline = LlmPipeline.builder(stubClient("direct response")).build();
        var request = LlmRequest.builder("Hello", "gpt-4o").build();

        var response = pipeline.execute(request);

        assertThat(response.content()).isEqualTo("direct response");
        assertThat(response.provider()).isEqualTo("stub");
    }

    @Test
    void modulesAreCalledInOrder() throws PolityException {
        var callOrder = new ArrayList<String>();
        var pipeline = LlmPipeline.builder(stubClient("ok"))
                .with(orderRecordingModule("first", callOrder))
                .with(orderRecordingModule("second", callOrder))
                .with(orderRecordingModule("third", callOrder))
                .build();

        pipeline.execute(LlmRequest.builder("Hello", "gpt-4o").build());

        assertThat(callOrder).containsExactly("first", "second", "third");
    }

    @Test
    void moduleCanModifyRequestBeforePassingOn() throws PolityException {
        // The mutating module rewrites the prompt.
        // The stub client echoes the prompt back in the response content
        // so we can verify what the client actually received.
        LlmClient echoClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                return LlmResponse.builder(
                        "received: " + request.prompt(),
                        request.model(), "stub").build();
            }
            @Override
            public String provider() { return "stub"; }
        };

        var pipeline = LlmPipeline.builder(echoClient)
                .with(requestMutatingModule("modified prompt"))
                .build();

        var response = pipeline.execute(
                LlmRequest.builder("original prompt", "gpt-4o").build());

        assertThat(response.content()).isEqualTo("received: modified prompt");
    }

    @Test
    void shortCircuitModuleSkipsClientAndRemainingModules() throws PolityException {
        // If the short-circuit module fires, the client should never be called.
        LlmClient shouldNotBeCalled = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                throw new AssertionError("client should not have been called");
            }
            @Override
            public String provider() { return "stub"; }
        };

        var callOrder = new ArrayList<String>();
        var pipeline = LlmPipeline.builder(shouldNotBeCalled)
                .with(orderRecordingModule("before-cache", callOrder))
                .with(shortCircuitModule("cached response"))
                .with(orderRecordingModule("after-cache", callOrder))
                .build();

        var response = pipeline.execute(
                LlmRequest.builder("Hello", "gpt-4o").build());

        assertThat(response.content()).isEqualTo("cached response");
        assertThat(response.provider()).isEqualTo("cache");
        assertThat(callOrder).containsExactly("before-cache");
        assertThat(callOrder).doesNotContain("after-cache");
    }

    @Test
    void abortingModulePropagatesException() {
        var pipeline = LlmPipeline.builder(stubClient("ok"))
                .with(abortingModule())
                .build();

        assertThatThrownBy(() ->
                pipeline.execute(LlmRequest.builder("Hello", "gpt-4o").build()))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void modulesAfterAbortAreNotCalled() {
        var callOrder = new ArrayList<String>();
        var pipeline = LlmPipeline.builder(stubClient("ok"))
                .with(orderRecordingModule("before-abort", callOrder))
                .with(abortingModule())
                .with(orderRecordingModule("after-abort", callOrder))
                .build();

        assertThatThrownBy(() ->
                pipeline.execute(LlmRequest.builder("Hello", "gpt-4o").build()))
                .isInstanceOf(BudgetExceededException.class);

        assertThat(callOrder).containsExactly("before-abort");
        assertThat(callOrder).doesNotContain("after-abort");
    }

    @Test
    void rejectsNullRequest() {
        var pipeline = LlmPipeline.builder(stubClient("ok")).build();

        assertThatThrownBy(() -> pipeline.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullModule() {
        assertThatThrownBy(() ->
                LlmPipeline.builder(stubClient("ok")).with(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // Additional Edge Cases
    // ------------------------------------------------------------------

    @Test
    void builderRejectsNullClient() {
        assertThatThrownBy(() -> LlmPipeline.builder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client must not be null");
    }

    @Test
    void multipleRequestMutationsAppliesSequentialChanges() throws PolityException {
        LlmClient echoClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                return LlmResponse.builder(request.prompt(), request.model(), "stub").build();
            }
            @Override
            public String provider() { return "stub"; }
        };

        var pipeline = LlmPipeline.builder(echoClient)
                .with(requestMutatingModule("A"))
                .with(requestMutatingModule("B"))
                .build();

        var response = pipeline.execute(LlmRequest.builder("start", "gpt-4o").build());
        assertThat(response.content()).isEqualTo("B");
    }
}
