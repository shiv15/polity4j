package io.polity4j.core.tool;

import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.ToolCall;
import io.polity4j.core.ToolSpec;
import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionModuleTest {

    @Test
    void testAutoToolExecutionLoop() throws PolityException {
        ToolSpec weatherSpec = ToolSpec.of("get_weather", "Fetch weather", Map.of());
        ToolRegistry registry = ToolRegistry.builder()
                .register(weatherSpec, args -> "72F Sunny")
                .build();

        ToolExecutionModule toolModule = new ToolExecutionModule(registry);

        AtomicInteger calls = new AtomicInteger(0);
        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) throws PolityException {
                int count = calls.incrementAndGet();
                if (count == 1) {
                    // Turn 1: Model requests tool call
                    return LlmResponse.builder("I need weather info", request.model(), provider())
                            .finishReason(FinishReason.TOOL_CALLS)
                            .toolCalls(List.of(new ToolCall("call_123", "get_weather", Map.of("location", "NYC"))))
                            .build();
                } else {
                    // Turn 2: Model receives tool result and completes
                    return LlmResponse.builder("The weather in NYC is 72F Sunny.", request.model(), provider())
                            .finishReason(FinishReason.STOP)
                            .build();
                }
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient)
                .with(toolModule)
                .build();

        LlmResponse response = pipeline.execute(LlmRequest.builder("What's the weather?", "gpt-4o").build());

        assertThat(calls.get()).isEqualTo(2);
        assertThat(response.content()).isEqualTo("The weather in NYC is 72F Sunny.");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }
}
