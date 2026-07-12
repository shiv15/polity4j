# Client Adapters

Adapters solve a key design constraint: **Polity4j never owns the HTTP client and never sees API keys.**

Instead, the caller brings their own HTTP client or SDK setup (with custom timeouts, proxy settings, and credentials), wraps it in a Polity4j adapter, and registers it to the pipeline.

---

## Core Flow

When a provider or infrastructure error occurs, different models return different formats or HTTP codes:
- **Anthropic** returns a `529` when overloaded, or a `429` with a `retry-after` header.
- **OpenAI** returns a `429` with custom error payloads.
- **SDKs** throw proprietary exceptions.

The adapters intercept these events and translate them into typed `PolityException` subtypes:

```
Provider Client / SDK throws HTTP 429 or 529
                     ↓
             Adapter intercepts
                     ↓
        Translates to RateLimitException
            or OverloadedException
            or ModelUnavailableException
                     ↓
      Pipeline modules react correctly
```

---

## Out-of-the-Box Adapters

The `polity4j-adapters` module provides two standard, zero-SDK adapters using plain Java `HttpClient` and Jackson:

### 1. `OpenAiAdapter`
Wraps the OpenAI Chat Completions API. It handles system roles and maps `429` rate limits and `500` internal errors to their respective Polity exceptions.

```java
import io.polity4j.adapters.openai.OpenAiAdapter;
import java.net.http.HttpClient;

// 1. Wrap your custom HttpClient and API key
LlmClient openAiClient = new OpenAiAdapter(
    HttpClient.newHttpClient(),
    System.getenv("OPENAI_API_KEY")
);
```

### 2. `AnthropicAdapter`
Wraps the Anthropic Messages API. It extracts system turns and formats them as the root-level `"system"` request parameter required by Anthropic, while mapping `529` to `OverloadedException` and `429` to `RateLimitException`.

```java
import io.polity4j.adapters.anthropic.AnthropicAdapter;
import java.net.http.HttpClient;

// 1. Wrap your custom HttpClient and API key
LlmClient anthropicClient = new AnthropicAdapter(
    HttpClient.newHttpClient(),
    System.getenv("ANTHROPIC_API_KEY")
);
```

---

## Example Usage with Pipeline

```java
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;

// Wrap clients in adapters
LlmClient primary = new OpenAiAdapter(apiKey);

// Instantiate pipeline
LlmPipeline pipeline = LlmPipeline.builder(primary)
    .with(new CircuitBreakerModule())
    .build();

// Execute requests normally
LlmResponse response = pipeline.execute(
    LlmRequest.builder("Explain recursion in 1 sentence", "gpt-4o").build()
);
System.out.println(response.content());
```

---

## Writing a Custom Adapter

If you are using Spring AI, LangChain4j, or custom internal client SDKs, you can implement the `LlmClient` interface:

```java
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ModelUnavailableException;

public class MyCustomAdapter implements LlmClient {

    private final MyInternalClient client;

    public MyCustomAdapter(MyInternalClient client) {
        this.client = client;
    }

    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        try {
            // Call client and parse response payload
            String rawResponse = client.send(request.prompt());
            return LlmResponse.builder(rawResponse, request.model(), provider())
                .build();
        } catch (MyRateLimitException e) {
            throw new RateLimitException(provider(), 5000L);
        } catch (Exception e) {
            throw new ModelUnavailableException(request.model(), provider(), e);
        }
    }

    @Override
    public String provider() {
        return "custom-provider";
    }
}
```
