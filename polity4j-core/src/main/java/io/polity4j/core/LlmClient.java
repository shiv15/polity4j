package io.polity4j.core;

import io.polity4j.core.exception.PolityException;

/**
 * The single interface every adapter implements.
 *
 * Polity never owns the HTTP client and never sees API keys.
 * The caller brings their existing client, wraps it in an adapter
 * that implements this interface, and passes it to the pipeline.
 *
 * Implementations must translate all provider-specific errors into
 * the appropriate PolityException subtype before throwing.
 */
public interface LlmClient {

    /**
     * Execute one AI call and return the response.
     *
     * @param request the fully prepared request
     * @return the provider response
     * @throws PolityException any provider or infrastructure failure,
     *                         translated into the appropriate subtype
     */
    LlmResponse call(LlmRequest request) throws PolityException;

    /**
     * The provider name this client connects to.
     * Used for logging, metrics, and circuit breaker keying.
     * Examples: "anthropic", "openai", "ollama"
     */
    String provider();
}
