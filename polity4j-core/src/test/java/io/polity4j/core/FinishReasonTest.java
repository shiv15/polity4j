package io.polity4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinishReasonTest {

    @Test
    void enumContainsAllExpectedValues() {
        assertThat(FinishReason.values()).containsExactlyInAnyOrder(
                FinishReason.STOP,
                FinishReason.LENGTH,
                FinishReason.CONTENT_FILTER,
                FinishReason.TOOL_CALLS,
                FinishReason.ERROR,
                FinishReason.UNKNOWN
        );
    }
}
