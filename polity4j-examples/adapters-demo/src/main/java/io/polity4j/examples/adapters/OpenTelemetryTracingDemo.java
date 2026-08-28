package io.polity4j.examples.adapters;

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
import io.polity4j.integrations.opentelemetry.GenAiAttributes;
import io.polity4j.integrations.opentelemetry.OpenTelemetryTracingModule;

import java.util.List;

public class OpenTelemetryTracingDemo {

    public static void main(String[] args) {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Tracer tracer = openTelemetry.getTracer("demo-tracer");
        OpenTelemetryTracingModule tracingModule = new OpenTelemetryTracingModule(tracer);

        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                return LlmResponse.builder("Polity4j provides enterprise-grade AI governance.", request.model(), provider())
                        .inputTokens(18)
                        .outputTokens(8)
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "openai";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient)
                .with(tracingModule)
                .build();

        LlmRequest request = LlmRequest.builder("Summarize Polity4j", "gpt-4o")
                .callerId("analytics-service")
                .temperature(0.2)
                .build();

        System.out.println("Executing OpenTelemetry traced request...");
        LlmResponse response = pipeline.execute(request);
        System.out.println("Response: " + response.content());

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        System.out.println("\n--- Exported OpenTelemetry Spans (" + spans.size() + ") ---");
        for (SpanData span : spans) {
            System.out.println("Span Name  : " + span.getName());
            System.out.println("Span Kind  : " + span.getKind());
            System.out.println("Status     : " + span.getStatus().getStatusCode());
            System.out.println("Model      : " + span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL));
            System.out.println("System     : " + span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM));
            System.out.println("Input Toks : " + span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS));
            System.out.println("Output Toks: " + span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS));
            System.out.println("Caller ID  : " + span.getAttributes().get(GenAiAttributes.POLITY_CALLER_ID));
        }
    }
}
