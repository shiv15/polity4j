package io.polity4j.core;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseTest {

    @Test
    void buildsWithRequiredFieldsOnly() {
        var response = LlmResponse.builder("Hello back", "claude-3-5-sonnet", "anthropic").build();
        assertThat(response.content()).isEqualTo("Hello back");
        assertThat(response.provider()).isEqualTo("anthropic");
        assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.finishReason()).isEqualTo(FinishReason.UNKNOWN);
    }

    @Test
    void builderSetsFinishReasonExplicitly() {
        var response = LlmResponse.builder("Hello back", "gpt-4o", "openai")
                .finishReason(FinishReason.STOP)
                .build();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void nullFinishReasonDefaultsToUnknown() {
        var response = new LlmResponse("Hi", "gpt-4o", "openai", 10, 5, BigDecimal.ZERO, 100L, null);
        assertThat(response.finishReason()).isEqualTo(FinishReason.UNKNOWN);
    }

    @Test
    void totalTokensSumsInputAndOutput() {
        var response = LlmResponse.builder("Hi", "gpt-4o", "openai")
                .inputTokens(100)
                .outputTokens(50)
                .build();
        assertThat(response.totalTokens()).isEqualTo(150);
    }

    @Test
    void rejectsNegativeTokenCounts() {
        assertThatThrownBy(() ->
            LlmResponse.builder("Hi", "gpt-4o", "openai")
                    .inputTokens(-1)
                    .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() ->
            LlmResponse.builder(null, "gpt-4o", "openai").build())
                .isInstanceOf(NullPointerException.class);
    }
}

