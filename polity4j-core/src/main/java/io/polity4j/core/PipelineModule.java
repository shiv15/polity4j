package io.polity4j.core;

import io.polity4j.core.exception.PolityException;

/**
 * A single composable unit in the Polity pipeline.
 *
 * Modules are applied in order by LlmPipeline. Each module receives
 * the request, can modify it, pass it along, short-circuit by returning
 * early, or throw to abort the pipeline.
 *
 * Implementations should be stateless where possible. Stateful modules
 * (circuit breaker, cache) must be thread-safe.
 */
public interface PipelineModule {

    /**
     * Process the request and return a response.
     *
     * The module may:
     * - Pass the request to the next module unchanged
     * - Modify the request before passing it on
     * - Return a response directly without calling next (cache hit, budget block)
     * - Throw PolityException to abort the pipeline
     *
     * @param request the incoming request
     * @param next    the next step in the pipeline — call this to continue
     * @return the response, either from next or produced directly
     * @throws PolityException if this module aborts the pipeline
     */
    LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException;

    /**
     * Human-readable name for logging and diagnostics.
     */
    String name();
}
