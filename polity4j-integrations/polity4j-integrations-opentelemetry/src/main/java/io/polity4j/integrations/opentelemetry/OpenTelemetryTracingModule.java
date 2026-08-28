package io.polity4j.integrations.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;

import java.util.List;
import java.util.Objects;

/**
 * PipelineModule that wraps LLM requests in OpenTelemetry CLIENT spans,
 * attaching OpenTelemetry GenAI Semantic Convention attributes.
 */
public final class OpenTelemetryTracingModule implements PipelineModule {

    public static final String TRACER_NAME = "io.polity4j.opentelemetry";
    public static final String SPAN_NAME = "polity4j.pipeline.execute";

    private final Tracer tracer;

    public OpenTelemetryTracingModule(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
    }

    public OpenTelemetryTracingModule(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.tracer = openTelemetry.getTracer(TRACER_NAME);
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");

        Span span = tracer.spanBuilder(SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(GenAiAttributes.GEN_AI_REQUEST_MODEL, request.model())
                .startSpan();

        if (request.callerId() != null) {
            span.setAttribute(GenAiAttributes.POLITY_CALLER_ID, request.callerId());
        }
        if (request.temperature() != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE, request.temperature());
        }
        if (request.topP() != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TOP_P, request.topP());
        }
        if (request.maxTokens() > 0) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS, (long) request.maxTokens());
        }

        try (Scope scope = span.makeCurrent()) {
            LlmResponse response = next.proceed(request);

            if (response.provider() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_SYSTEM, response.provider());
            }
            if (response.model() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_MODEL, response.model());
            }
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, (long) response.inputTokens());
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, (long) response.outputTokens());
            if (response.finishReason() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS, List.of(response.finishReason().name().toLowerCase()));
            }
            if (response.estimatedCost() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_USAGE_COST, response.estimatedCost().doubleValue());
            }

            span.setStatus(StatusCode.OK);
            return response;
        } catch (Throwable t) {
            span.recordException(t);
            span.setAttribute(GenAiAttributes.EXCEPTION_TYPE, t.getClass().getName());
            span.setStatus(StatusCode.ERROR, t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }

    @Override
    public String name() {
        return "opentelemetry-tracing";
    }
}
