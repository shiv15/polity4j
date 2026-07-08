package io.polity4j.quality.response;

import java.util.Optional;

/**
 * The result of validating an LlmResponse.
 *
 * Either a success (no reason needed) or a failure with an optional
 * human-readable reason that is used to construct the corrective
 * prompt on retry.
 *
 * If failureReason is empty on a failure, the module falls back to
 * a default corrective prompt.
 */
public final class ValidationResult {

    private static final ValidationResult SUCCESS = new ValidationResult(true, null);

    private final boolean valid;
    private final String failureReason;

    private ValidationResult(boolean valid, String failureReason) {
        this.valid = valid;
        this.failureReason = failureReason;
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failure(String reason) {
        return new ValidationResult(false, reason);
    }

    public boolean isValid() {
        return valid;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }
}
