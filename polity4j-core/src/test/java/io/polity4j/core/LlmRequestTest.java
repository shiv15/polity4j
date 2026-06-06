package io.polity4j.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    @Test
    void buildsWithRequiredFieldsOnly() {
        var request = LlmRequest.builder("Hello", "claude-3-5-sonnet").build();
        assertThat(request.prompt()).isEqualTo("Hello");
        assertThat(request.model()).isEqualTo("claude-3-5-sonnet");
        assertThat(request.maxTokens()).isEqualTo(1024);
        assertThat(request.conversationHistory()).isEmpty();
    }

    @Test
    void buildsWithAllFields() {
        var history = List.of(new LlmRequest.Message("user", "Hi"));
        var request = LlmRequest.builder("Hello", "gpt-4o")
                .maxTokens(2048)
                .callerId("service-a")
                .regionContext("eu-west-1")
                .conversationHistory(history)
                .build();

        assertThat(request.callerId()).isEqualTo("service-a");
        assertThat(request.regionContext()).isEqualTo("eu-west-1");
        assertThat(request.conversationHistory()).hasSize(1);
    }

    @Test
    void rejectsNullPrompt() {
        assertThatThrownBy(() ->
            LlmRequest.builder(null, "gpt-4o").build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankPrompt() {
        assertThatThrownBy(() ->
            LlmRequest.builder("  ", "gpt-4o").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxTokens() {
        assertThatThrownBy(() ->
            LlmRequest.builder("Hello", "gpt-4o").maxTokens(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversationHistoryIsImmutable() {
        var request = LlmRequest.builder("Hello", "gpt-4o").build();
        assertThatThrownBy(() ->
            request.conversationHistory().add(new LlmRequest.Message("user", "Hi")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
