package io.polity4j.reliability.fallback;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tries the primary pipeline first. If it throws a fallback-eligible
 * exception, works through an ordered list of fallback clients until
 * one succeeds or all are exhausted.
 *
 * Fallback-eligible exceptions:
 *   ModelUnavailableException — provider is down
 *   OverloadedException       — provider is saturated
 *   RateLimitException        — provider rate limited
 *
 * Non-fallback exceptions propagate immediately without trying
 * fallbacks — a BudgetExceededException or ContextOverflowException
 * is not a provider problem and a different provider won't help.
 *
 * Usage:
 *   LlmPipeline.builder(primaryClient)
 *       .with(new FallbackChainModule(List.of(anthropicClient, ollamaClient)))
 *       .build();
 */
public final class FallbackChainModule implements PipelineModule {

    private final List<LlmClient> fallbacks;

    public FallbackChainModule(List<LlmClient> fallbacks) {
        Objects.requireNonNull(fallbacks, "fallbacks must not be null");
        if (fallbacks.isEmpty()) {
            throw new IllegalArgumentException(
                    "fallbacks must not be empty — use at least one fallback client");
        }
        this.fallbacks = List.copyOf(fallbacks);
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {

        // Try the primary pipeline first
        try {
            return next.proceed(request);
        } catch (PolityException primaryException) {
            if (!isFallbackEligible(primaryException)) {
                throw primaryException;
            }

            // Primary failed with a provider error — try fallbacks in order
            List<PolityException> allFailures = new ArrayList<>();
            allFailures.add(primaryException);

            for (LlmClient fallback : fallbacks) {
                try {
                    return fallback.call(request);
                } catch (PolityException fallbackException) {
                    allFailures.add(fallbackException);
                    if (!isFallbackEligible(fallbackException)) {
                        // Fallback threw a non-provider error — stop trying
                        throw fallbackException;
                    }
                    // Provider error on this fallback — try the next one
                }
            }

            // All fallbacks exhausted — throw a ModelUnavailableException
            // that summarizes the situation
            throw new ModelUnavailableException(
                    request.model(),
                    "all-providers-exhausted",
                    buildCause(allFailures));
        }
    }

    @Override
    public String name() { return "fallback-chain"; }

    private boolean isFallbackEligible(PolityException e) {
        return e instanceof ModelUnavailableException
                || e instanceof io.polity4j.core.exception.OverloadedException
                || e instanceof io.polity4j.core.exception.RateLimitException;
    }

    /**
     * Chains all failure causes together so the final exception carries
     * a complete record of what was tried and why each failed.
     */
    private Throwable buildCause(List<PolityException> failures) {
        if (failures.isEmpty()) return null;
        Throwable root = failures.get(0);
        Throwable current = root;
        for (int i = 1; i < failures.size(); i++) {
            current.initCause(failures.get(i));
            current = failures.get(i);
        }
        return root;
    }
}
