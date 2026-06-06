package io.polity4j.core;

import io.polity4j.core.exception.PolityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The central orchestrator of the Polity pipeline.
 *
 * Accepts an ordered list of PipelineModules and an LlmClient.
 * On each execute() call, builds a chain from the modules and
 * the client, then fires the chain from the front.
 *
 * The chain is built right-to-left:
 *
 *   [module-0] → [module-1] → [module-2] → [client.call()]
 *
 * Each module receives the remainder of the chain as its `next`
 * argument. Calling next.proceed() passes control rightward.
 * Not calling it short-circuits everything to the right.
 *
 * Thread safety: LlmPipeline itself is stateless after construction.
 * Thread safety of the pipeline as a whole depends on the modules —
 * stateful modules (circuit breaker, cache) must be thread-safe
 * independently.
 */
public final class LlmPipeline {

    private final List<PipelineModule> modules;
    private final LlmClient client;

    private LlmPipeline(List<PipelineModule> modules, LlmClient client) {
        this.modules = List.copyOf(modules);
        this.client = client;
    }

    /**
     * Execute the pipeline for one request.
     *
     * Builds the chain fresh on each call — this is intentional.
     * Modules may carry state (circuit breaker open/closed) but the
     * chain structure itself is stateless and cheap to construct.
     *
     * @param request the request to process
     * @return the response from the first module that produces one,
     *         which is usually the LlmClient at the end of the chain
     * @throws PolityException if any module or the client aborts the call
     */
    public LlmResponse execute(LlmRequest request) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        PipelineChain chain = buildChain();
        return chain.proceed(request);
    }

    /**
     * Builds the execution chain right-to-left.
     *
     * Start with the terminal — a PipelineChain that calls the client.
     * Then wrap it with each module in reverse order, so that module-0
     * ends up at the front and the client call ends up at the back.
     *
     * For a pipeline with modules [A, B, C] and client K:
     *
     *   terminal = req -> K.call(req)
     *   after C:  req -> C.process(req, terminal)
     *   after B:  req -> B.process(req, chain-containing-C)
     *   after A:  req -> A.process(req, chain-containing-B-and-C)
     *
     * Calling the final chain invokes A, which may invoke B, which
     * may invoke C, which may invoke K.
     */
    private PipelineChain buildChain() {
        // Terminal: the actual API call
        PipelineChain chain = request -> client.call(request);

        // Wrap right-to-left
        for (int i = modules.size() - 1; i >= 0; i--) {
            PipelineModule module = modules.get(i);
            PipelineChain next = chain;
            chain = request -> module.process(request, next);
        }

        return chain;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(LlmClient client) {
        return new Builder(client);
    }

    public static final class Builder {
        private final LlmClient client;
        private final List<PipelineModule> modules = new ArrayList<>();

        private Builder(LlmClient client) {
            this.client = Objects.requireNonNull(client, "client must not be null");
        }

        public Builder with(PipelineModule module) {
            Objects.requireNonNull(module, "module must not be null");
            modules.add(module);
            return this;
        }

        public LlmPipeline build() {
            return new LlmPipeline(modules, client);
        }
    }
}
