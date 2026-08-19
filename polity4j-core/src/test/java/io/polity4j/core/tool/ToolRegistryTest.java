package io.polity4j.core.tool;

import io.polity4j.core.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    static class SampleBeanService {
        @PolityTool(name = "check_inventory", description = "Checks stock for item")
        public String checkInventory(String itemId) {
            return "In Stock: 42 units for " + itemId;
        }
    }

    @Test
    void testExplicitRegistration() throws Exception {
        ToolSpec spec = ToolSpec.of("calculator", "Add numbers", Map.of());
        ToolRegistry registry = ToolRegistry.builder()
                .register(spec, args -> "100")
                .build();

        assertThat(registry.specs()).containsExactly(spec);
        assertThat(registry.getHandler("calculator")).isNotNull();
        assertThat(registry.getHandler("calculator").execute(Map.of())).isEqualTo("100");
    }

    @Test
    void testBeanRegistration() throws Exception {
        SampleBeanService service = new SampleBeanService();
        ToolRegistry registry = ToolRegistry.builder()
                .registerBean(service)
                .build();

        assertThat(registry.getHandler("check_inventory")).isNotNull();
        Object result = registry.getHandler("check_inventory").execute(Map.of("itemId", "SKU-99"));
        assertThat(result).isEqualTo("In Stock: 42 units for SKU-99");
    }
}
