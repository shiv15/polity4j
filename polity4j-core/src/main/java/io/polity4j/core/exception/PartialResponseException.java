package io.polity4j.core.exception;

// Stream closed mid-response with no HTTP error
public final class PartialResponseException extends PolityException {

    private final String partialContent;

    public PartialResponseException(String partialContent) {
        super("Response stream terminated before completion", 502);
        this.partialContent = partialContent;
    }

    public PartialResponseException(String partialContent, Throwable cause) {
        super("Response stream terminated before completion", 502, cause);
        this.partialContent = partialContent;
    }

    public String partialContent() { return partialContent; }
}
