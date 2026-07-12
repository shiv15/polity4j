package io.polity4j.integrations.spring;

import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.exception.PolityException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.Objects;

/**
 * AOP aspect that intercepts @Polity4jGuarded methods and routes
 * the LlmRequest through the configured LlmPipeline.
 *
 * The intercepted method must:
 *   - Accept exactly one LlmRequest parameter
 *   - Return LlmResponse
 *
 * The method body is never executed — the pipeline handles the call.
 */
@Aspect
public class Polity4jGuardedAspect {

    private final LlmPipeline pipeline;

    public Polity4jGuardedAspect(LlmPipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
    }

    @Around("@annotation(io.polity4j.integrations.spring.Polity4jGuarded)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        LlmRequest request = extractRequest(joinPoint);

        if (request == null) {
            throw new IllegalStateException(
                    "@Polity4jGuarded method must have exactly one "
                    + "LlmRequest parameter: "
                    + joinPoint.getSignature().toShortString());
        }

        try {
            return pipeline.execute(request);
        } catch (PolityException e) {
            throw e;
        }
    }

    private LlmRequest extractRequest(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof LlmRequest request) {
                return request;
            }
        }
        return null;
    }
}
