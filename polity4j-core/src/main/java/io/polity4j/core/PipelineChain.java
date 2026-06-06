package io.polity4j.core;

import io.polity4j.core.exception.PolityException;

/**
 * Represents the remainder of the pipeline after the current module.
 * Calling proceed() passes control to the next module in sequence,
 * or to the LlmClient if no more modules remain.
 *
 * This is the chain-of-responsibility pattern — each module decides
 * whether to call proceed() or short-circuit.
 */
@FunctionalInterface
public interface PipelineChain {

    LlmResponse proceed(LlmRequest request) throws PolityException;
}
