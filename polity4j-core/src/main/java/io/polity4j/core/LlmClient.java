package io.polity4j.core;

import io.polity4j.core.exception.PolityException;

import java.util.function.Consumer;

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
     * Execute one AI call with real-time token streaming.
     *
     * Chunks are emitted to tokenHandler as they arrive.
     * Returns the aggregated final response when complete.
     *
     * @param request the fully prepared request
     * @param tokenHandler consumer receiving each generated token string
     * @return the aggregated provider response
     * @throws PolityException any provider or infrastructure failure
     */
    default LlmResponse callStreaming(LlmRequest request, Consumer<String> tokenHandler) throws PolityException {
        LlmResponse response = call(request);
        if (tokenHandler != null && response.content() != null) {
            tokenHandler.accept(response.content());
        }
        return response;
    }

    /**
     * The provider name this client connects to.
     * Used for logging, metrics, and circuit breaker keying.
     * Examples: "anthropic", "openai", "ollama"
     */
    String provider();
}
