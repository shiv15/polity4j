package io.polity4j.examples.full;

import io.polity4j.adapters.openai.OpenAiAdapter;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.tool.PolityTool;
import io.polity4j.core.tool.ToolExecutionModule;
import io.polity4j.core.tool.ToolRegistry;

import java.net.http.HttpClient;

public class ToolExecutionDemo {

    public static class InventoryService {
        @PolityTool(name = "get_stock_level", description = "Get remaining stock level for a SKU")
        public String getStockLevel(String sku) {
            return "SKU " + sku + " has 48 items available in warehouse-east.";
        }
    }

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("OPENAI_API_KEY environment variable not set. Demo running in dry-run mode.");
            return;
        }

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), apiKey);

        InventoryService inventoryService = new InventoryService();
        ToolRegistry registry = ToolRegistry.builder()
                .registerBean(inventoryService)
                .build();

        LlmPipeline pipeline = LlmPipeline.builder(adapter)
                .with(new ToolExecutionModule(registry))
                .build();

        LlmRequest request = LlmRequest.builder(
                "Check stock level for SKU-779 and confirm availability.",
                "gpt-4o"
        ).build();

        System.out.println("Executing request with auto-executing ToolExecutionModule...");
        LlmResponse response = pipeline.execute(request);

        System.out.println("Final Result: " + response.content());
        System.out.println("Finish Reason: " + response.finishReason());
        System.out.println("Total Cost: $" + response.estimatedCost());
    }
}
