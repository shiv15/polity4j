package io.polity4j.core.exception;

// Provider is down — circuit breaker uses this to trip open
public final class ModelUnavailableException extends PolityException {

    private final String model;
    private final String provider;

    public ModelUnavailableException(String model, String provider) {
        super("Model '" + model + "' unavailable at provider '" + provider + "'", 503);
        this.model = model;
        this.provider = provider;
    }

    public ModelUnavailableException(String model, String provider, Throwable cause) {
        super("Model '" + model + "' unavailable at provider '" + provider + "'", 503, cause);
        this.model = model;
        this.provider = provider;
    }

    public String model() { return model; }
    public String provider() { return provider; }
}
