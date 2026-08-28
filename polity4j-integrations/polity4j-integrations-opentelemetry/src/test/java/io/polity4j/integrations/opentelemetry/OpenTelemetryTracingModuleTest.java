package io.polity4j.integrations.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.ModelUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenTelemetryTracingModuleTest {

    private InMemorySpanExporter spanExporter;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        tracer = openTelemetry.getTracer("test-tracer");
    }

    @Test
    void testSuccessfulPipelineExecutionRecordsGenAiAttributes() {
        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                // Verify active span context inside execution
                assertThat(Span.current().getSpanContext().isValid()).isTrue();

                return LlmResponse.builder("Hello World", request.model(), provider())
                        .inputTokens(10)
                        .outputTokens(5)
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "openai";
            }
        };

        OpenTelemetryTracingModule tracingModule = new OpenTelemetryTracingModule(tracer);
        LlmPipeline pipeline = LlmPipeline.builder(mockClient)
                .with(tracingModule)
                .build();

        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o")
                .callerId("finance-service")
                .temperature(0.7)
                .maxTokens(100)
                .build();

        LlmResponse response = pipeline.execute(request);

        assertThat(response.content()).isEqualTo("Hello World");

        List<SpanData> finishedSpans = spanExporter.getFinishedSpanItems();
        assertThat(finishedSpans).hasSize(1);

        SpanData spanData = finishedSpans.get(0);
        assertThat(spanData.getName()).isEqualTo(OpenTelemetryTracingModule.SPAN_NAME);
        assertThat(spanData.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify GenAI Attributes
        assertThat(spanData.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(spanData.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(spanData.getAttributes().get(GenAiAttributes.POLITY_CALLER_ID)).isEqualTo("finance-service");
        assertThat(spanData.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(10L);
        assertThat(spanData.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(5L);
        assertThat(spanData.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS)).containsExactly("stop");
    }

    @Test
    void testPipelineFailureRecordsErrorSpanAndException() {
        LlmClient failingClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                throw new ModelUnavailableException(request.model(), provider());
            }

            @Override
            public String provider() {
                return "openai";
            }
        };

        OpenTelemetryTracingModule tracingModule = new OpenTelemetryTracingModule(tracer);
        LlmPipeline pipeline = LlmPipeline.builder(failingClient)
                .with(tracingModule)
                .build();

        LlmRequest request = LlmRequest.builder("Hi", "gpt-4o").build();

        assertThatThrownBy(() -> pipeline.execute(request))
                .isInstanceOf(ModelUnavailableException.class);

        List<SpanData> finishedSpans = spanExporter.getFinishedSpanItems();
        assertThat(finishedSpans).hasSize(1);

        SpanData spanData = finishedSpans.get(0);
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(spanData.getAttributes().get(GenAiAttributes.EXCEPTION_TYPE))
                .isEqualTo(ModelUnavailableException.class.getName());
    }
}
