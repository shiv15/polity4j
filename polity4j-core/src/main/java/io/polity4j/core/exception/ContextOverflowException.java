package io.polity4j.core.exception;

// Prompt exceeds model context window
public final class ContextOverflowException extends PolityException {

    private final int tokenCount;
    private final int contextLimit;

    public ContextOverflowException(int tokenCount, int contextLimit) {
        super("Prompt exceeds context window: " + tokenCount
              + " tokens, limit is " + contextLimit, 400);
        this.tokenCount = tokenCount;
        this.contextLimit = contextLimit;
    }

    public int tokenCount() { return tokenCount; }
    public int contextLimit() { return contextLimit; }
}
