# Polity4j

Polity4j is a lightweight, zero-dependency reliability orchestration framework for LLM integrations in Java 17+.

Polity4j decouples your LLM logic from reliability concerns (retry, backoff, rate limit handling, circuit breaking, fallback routing) using a pluggable, modular pipeline. It allows you to build resilient, fault-tolerant AI features using any HTTP client or SDK.

---

## Key Features

- 🔌 **Zero SDK lock-in**: Bring your own HTTP client or LLM SDK; adapt it easily by implementing the `LlmClient` interface.
- 🔁 **Resilient Retries**: Retry transient blips (like rate limits or brief provider failures) with customizable exponential backoff.
- 🛡️ **Circuit Breaking**: Fail fast during sustained outages to prevent cascade failure and avoid wasting rate limit budgets.
- 🔀 **Fallback Chains**: Route requests seamlessly to alternative models or providers (e.g., fallback from Claude to OpenAI or Ollama) when the primary client fails.
- 🔒 **Sealed Exception Hierarchy**: Domain exceptions (`PolityException`) map cleanly to standardized HTTP error behaviors.

---

## Project Structure

Polity4j is organized as a multi-module Maven project:

- **[`polity4j-core`](./polity4j-core)**: Core pipelines, request/response models, interfaces, and the exception hierarchy.
- **[`polity4j-reliability`](./polity4j-reliability)**: Pluggable reliability modules: `RetryModule`, `CircuitBreakerModule`, and `FallbackChainModule`.
- **[`polity4j-examples`](./polity4j-examples)**: A real-world example pipeline using a custom Java `HttpClient` adapter communicating with the Anthropic Messages API.

---

## Installation

Add the parent project or individual dependencies to your `pom.xml`:

```xml
<dependency>
  <groupId>io.polity4j</groupId>
  <artifactId>polity4j-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.polity4j</groupId>
  <artifactId>polity4j-reliability</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Quick Start

### 1. Implement `LlmClient`
Adapt any client or SDK to the pipeline:

```java
public class MyCustomClient implements LlmClient {
    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        // execute raw HTTP call or SDK invocation
        // map client errors to PolityException (e.g. RateLimitException, ModelUnavailableException)
        return LlmResponse.builder("response content", request.model(), provider()).build();
    }

    @Override
    public String provider() {
        return "custom-provider";
    }
}
```

### 2. Configure the Pipeline
Chain the core and reliability modules together:

```java
LlmClient primaryClient = new MyCustomClient();
LlmClient fallbackClient = new AnotherClient();

LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
    // 1. Retry up to 3 times on transient issues
    .with(new RetryModule(RetryConfig.DEFAULT))
    // 2. Trip open after 5 consecutive failures
    .with(new CircuitBreakerModule("primary-provider", CircuitBreakerConfig.DEFAULT))
    // 3. Roll over to secondary provider if primary fails
    .with(new FallbackChainModule(List.of(fallbackClient)))
    .build();
```

### 3. Execute Requests
Send requests through the pipeline:

```java
LlmRequest request = LlmRequest.builder(
    "Explain quantum computing in one sentence.", 
    "gpt-4o")
    .maxTokens(128)
    .build();

try {
    LlmResponse response = pipeline.execute(request);
    System.out.println("Result: " + response.content());
} catch (PolityException e) {
    System.err.println("Pipeline failed completely: " + e.getMessage());
}
```

---

## Running Examples

To run the live reliability demo using the Anthropic API:

1. Obtain an API key from Anthropic.
2. Set it in your environment:
   ```bash
   export ANTHROPIC_API_KEY=your-api-key
   ```
3. Run the exec plugin from the project root:
   ```bash
   mvn clean install -DskipTests
   mvn exec:java -pl polity4j-examples/reliability-demo
   ```

---

## License

Polity4j is licensed under the [MIT License](./LICENSE).
