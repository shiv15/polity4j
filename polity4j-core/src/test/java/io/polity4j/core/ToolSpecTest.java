package io.polity4j.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolSpecTest {

    @Test
    void testToolSpecCreation() {
        var spec = ToolSpec.of("get_weather", "Fetch current weather", Map.of("type", "object"));
        assertThat(spec.name()).isEqualTo("get_weather");
        assertThat(spec.description()).isEqualTo("Fetch current weather");
        assertThat(spec.parameters()).containsEntry("type", "object");
    }

    @Test
    void rejectsNullOrBlankName() {
        assertThatThrownBy(() -> new ToolSpec(null, "desc", Map.of()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ToolSpec("  ", "desc", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
