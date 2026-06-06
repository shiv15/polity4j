package io.polity4j.core.exception;

// 529 — do NOT retry same provider, route to alternative
public final class OverloadedException extends PolityException {

    private final String provider;

    public OverloadedException(String provider) {
        super("Provider '" + provider + "' is overloaded — route to alternative", 529);
        this.provider = provider;
    }

    public OverloadedException(String provider, Throwable cause) {
        super("Provider '" + provider + "' is overloaded — route to alternative", 529, cause);
        this.provider = provider;
    }

    public String provider() { return provider; }
}
