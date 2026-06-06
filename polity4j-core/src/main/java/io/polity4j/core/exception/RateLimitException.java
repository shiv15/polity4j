package io.polity4j.core.exception;

// 429 — wait and retry same provider
public final class RateLimitException extends PolityException {

    private final String provider;
    private final long retryAfterMs;

    public RateLimitException(String provider, long retryAfterMs) {
        super("Rate limit exceeded for provider '" + provider
              + "', retry after " + retryAfterMs + "ms", 429);
        this.provider = provider;
        this.retryAfterMs = retryAfterMs;
    }

    public String provider() { return provider; }
    public long retryAfterMs() { return retryAfterMs; }
}
