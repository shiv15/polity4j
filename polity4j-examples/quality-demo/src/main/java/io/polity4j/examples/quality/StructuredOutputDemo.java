package io.polity4j.examples.quality;

import io.polity4j.adapters.openai.OpenAiAdapter;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.quality.structured.StructuredOutputPipeline;
import io.polity4j.quality.structured.StructuredResult;

import java.net.http.HttpClient;

public class StructuredOutputDemo {

    public record WeatherReport(
            String location,
            double temperatureFahrenheit,
            String condition,
            int humidityPercent
    ) {}

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("OPENAI_API_KEY environment variable not set. Demo running in dry-run mode.");
            return;
        }

        OpenAiAdapter adapter = new OpenAiAdapter(HttpClient.newHttpClient(), apiKey);
        LlmPipeline pipeline = LlmPipeline.builder(adapter).build();

        StructuredOutputPipeline<WeatherReport> structuredPipeline = StructuredOutputPipeline.of(pipeline, WeatherReport.class);

        LlmRequest request = LlmRequest.builder(
                "Generate a current weather report for Tokyo, Japan.",
                "gpt-4o"
        ).build();

        System.out.println("Executing StructuredOutputPipeline...");
        StructuredResult<WeatherReport> result = structuredPipeline.execute(request);

        WeatherReport report = result.value();
        System.out.println("Deserialized Location  : " + report.location());
        System.out.println("Temperature (F)        : " + report.temperatureFahrenheit() + "°F");
        System.out.println("Condition              : " + report.condition());
        System.out.println("Humidity               : " + report.humidityPercent() + "%");
        System.out.println("Total Cost             : $" + result.rawResponse().estimatedCost());
        System.out.println("Retries Performed      : " + result.retries());
    }
}
