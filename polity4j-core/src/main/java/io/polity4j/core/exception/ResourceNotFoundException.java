package io.polity4j.core.exception;

/**
 * Exception thrown when a requested resource (e.g. user, article, comment) is not found.
 * Maps to HTTP 404 Not Found.
 */
public final class ResourceNotFoundException extends PolityException {
    public ResourceNotFoundException(String message) {
        super(message, 404);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, 404, cause);
    }
}
