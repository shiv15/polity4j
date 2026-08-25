package io.polity4j.quality.structured;

import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ResponseValidationException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputPipelineTest {

    public record UserProfile(String name, int age, String email) {}

    @Test
    void testCleanJsonParsing() throws PolityException {
        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                return LlmResponse.builder("{\"name\":\"Alice\",\"age\":30,\"email\":\"alice@example.com\"}", request.model(), provider())
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient).build();
        StructuredOutputPipeline<UserProfile> structuredPipeline = StructuredOutputPipeline.of(pipeline, UserProfile.class);

        StructuredResult<UserProfile> result = structuredPipeline.execute(LlmRequest.builder("Get user info", "gpt-4o").build());

        assertThat(result.value().name()).isEqualTo("Alice");
        assertThat(result.value().age()).isEqualTo(30);
        assertThat(result.value().email()).isEqualTo("alice@example.com");
        assertThat(result.retries()).isEqualTo(0);
    }

    @Test
    void testStrippingMarkdownCodeFences() throws PolityException {
        String fencedJson = """
                ```json
                {
                  "name": "Bob",
                  "age": 25,
                  "email": "bob@example.com"
                }
                ```
                """;

        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                return LlmResponse.builder(fencedJson, request.model(), provider())
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient).build();
        StructuredOutputPipeline<UserProfile> structuredPipeline = StructuredOutputPipeline.of(pipeline, UserProfile.class);

        StructuredResult<UserProfile> result = structuredPipeline.execute(LlmRequest.builder("Get user info", "gpt-4o").build());

        assertThat(result.value().name()).isEqualTo("Bob");
        assertThat(result.value().age()).isEqualTo(25);
    }

    @Test
    void testAutoCorrectionRetryLoop() throws PolityException {
        AtomicInteger attempts = new AtomicInteger(0);

        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                int count = attempts.incrementAndGet();
                if (count == 1) {
                    // Turn 1: Malformed JSON syntax error
                    return LlmResponse.builder("{ name: 'Charlie', age: 40, email: invalid }", request.model(), provider())
                            .finishReason(FinishReason.STOP)
                            .build();
                } else {
                    // Turn 2: Valid JSON
                    return LlmResponse.builder("{\"name\":\"Charlie\",\"age\":40,\"email\":\"charlie@example.com\"}", request.model(), provider())
                            .finishReason(FinishReason.STOP)
                            .build();
                }
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient).build();
        StructuredOutputPipeline<UserProfile> structuredPipeline = StructuredOutputPipeline.of(pipeline, UserProfile.class);

        StructuredResult<UserProfile> result = structuredPipeline.execute(LlmRequest.builder("Get user info", "gpt-4o").build());

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(result.retries()).isEqualTo(1);
        assertThat(result.value().name()).isEqualTo("Charlie");
    }

    @Test
    void testThrowsExceptionWhenMaxRetriesExceeded() {
        LlmClient mockClient = new LlmClient() {
            @Override
            public LlmResponse call(LlmRequest request) {
                return LlmResponse.builder("Invalid JSON text always", request.model(), provider())
                        .finishReason(FinishReason.STOP)
                        .build();
            }

            @Override
            public String provider() {
                return "mock";
            }
        };

        LlmPipeline pipeline = LlmPipeline.builder(mockClient).build();
        StructuredOutputPipeline<UserProfile> structuredPipeline = StructuredOutputPipeline.of(pipeline, UserProfile.class);

        assertThatThrownBy(() -> structuredPipeline.execute(LlmRequest.builder("Get user info", "gpt-4o").build()))
                .isInstanceOf(ResponseValidationException.class)
                .hasMessageContaining("Failed to deserialize response into");
    }
}
