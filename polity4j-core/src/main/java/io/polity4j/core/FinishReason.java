package io.polity4j.core;

/**
 * Indicates the reason why the LLM generation finished.
 */
public enum FinishReason {

    /**
     * Model completed generation naturally.
     */
    STOP,

    /**
     * Generation stopped because the token limit (maxTokens) was reached.
     */
    LENGTH,

    /**
     * Output was flagged or blocked by content safety filters.
     */
    CONTENT_FILTER,

    /**
     * Model stopped to invoke one or more external tools / functions.
     */
    TOOL_CALLS,

    /**
     * Provider reported a generation error condition.
     */
    ERROR,

    /**
     * Unrecognized or unmapped finish reason returned by the provider.
     */
    UNKNOWN
}
