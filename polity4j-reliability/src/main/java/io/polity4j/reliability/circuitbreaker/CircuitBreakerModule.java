package io.polity4j.reliability.circuitbreaker;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.BudgetExceededException;
import io.polity4j.core.exception.ContextOverflowException;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;
import io.polity4j.core.exception.OverloadedException;
import io.polity4j.core.exception.RateLimitException;
import io.polity4j.core.exception.ResourceNotFoundException;
import io.polity4j.core.exception.PartialResponseException;

import java.util.Objects;

/**
 * Pipeline module that wraps a CircuitBreaker around downstream calls.
 *
 * Provider errors (RateLimitException, OverloadedException,
 * ModelUnavailableException, PartialResponseException) count as failures — they
 * indicate the provider is struggling.
 *
 * Application errors (BudgetExceededException, ContextOverflowException,
 * ResourceNotFoundException) do NOT count as failures — the provider is fine,
 * our configuration or prompt rejected the call.
 *
 * When the circuit is OPEN, throws ModelUnavailableException immediately
 * without calling downstream at all.
 */
public final class CircuitBreakerModule implements PipelineModule {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerModule(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    /** Convenience constructor — creates the CircuitBreaker internally */
    public CircuitBreakerModule(String provider, CircuitBreakerConfig config) {
        this(new CircuitBreaker(
                Objects.requireNonNull(provider, "provider must not be null"),
                Objects.requireNonNull(config, "config must not be null")
        ));
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next)
            throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");

        if (!circuitBreaker.allowCall()) {
            System.err.printf("WARNING: Blocked call to provider '%s' because circuit breaker is OPEN.%n", circuitBreaker.provider());
            throw new ModelUnavailableException(
                    request.model(), circuitBreaker.provider());
        }

        try {
            LlmResponse response = next.proceed(request);
            circuitBreaker.recordSuccess();
            return response;

        } catch (RateLimitException e) {
            circuitBreaker.recordFailure();
            throw e;
        } catch (OverloadedException e) {
            circuitBreaker.recordFailure();
            throw e;
        } catch (ModelUnavailableException e) {
            circuitBreaker.recordFailure();
            throw e;
        } catch (PartialResponseException e) {
            circuitBreaker.recordFailure();
            throw e;
        } catch (BudgetExceededException e) {
            // Application error — provider is fine, do not count as failure
            throw e;
        } catch (ContextOverflowException e) {
            // Application error — provider is fine, do not count as failure
            throw e;
        } catch (ResourceNotFoundException e) {
            // Application error — provider is fine, do not count as failure
            throw e;
        } catch (PolityException e) {
            // Any other PolityException — count as failure
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public String name() { return "circuit-breaker:" + circuitBreaker.provider(); }

    public CircuitState state() { return circuitBreaker.state(); }
}
