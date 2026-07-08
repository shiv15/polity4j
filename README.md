# Polity4j

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://img.shields.io/badge/Java-17%2B-blue.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](./LICENSE)
[![Build Status](https://img.shields.io/badge/Build-passing-brightgreen.svg)](#)

Polity4j is a featherweight, **zero-dependency** reliability, cost, and quality orchestration framework for LLM integrations in Java 17+.

Unlike heavy frameworks (e.g. LangChain4j), Polity4j decouples your LLM business logic from operational concerns using a highly customizable, pluggable pipeline. It integrates with any HTTP client or SDK.

```mermaid
graph LR
    Req[LlmRequest] --> Q[Quality: PromptOptimizer]
    Q --> C_Route[Cost: ModelRouter]
    C_Route --> C_Cache[Cost: ExactCache]
    C_Cache --> R_Retry[Reliability: Retry]
    R_Retry --> R_CB[Reliability: CircuitBreaker]
    R_CB --> C_Budget[Cost: BudgetGuardrail]
    C_Budget --> Client[LlmClient / LLM Provider]
```

---

## Key Features

- 🔌 **Zero SDK Lock-In**: Bring your own HTTP client or LLM SDK.
- 🔁 **Resilient Retries**: Automatic backoff handling for rate limits and server errors.
- 🛡️ **Circuit Breaking**: Fast-fail sustained provider outages to save resources.
- 🔀 **Fallback Chains**: Failover to secondary models/providers when primary fails.
- 💰 **Budget Guardrails**: Set hard cumulative spend ceilings at call, caller, and org levels.
- ⚡ **Exact Cache**: Short-circuit identical calls using SHA-256 keying.
- 🤖 **Model Routing**: Route prompts dynamically to cheaper models based on text complexity.
- 📝 **Prompt Optimizer**: Clean prompts, deduplicate history, and prevent context overflow.
- 🔍 **Response Validator**: Validate LLM response structures (e.g., JSON schemas) and trigger corrective retry loops.

---

## Project Structure

Polity4j is organized as a modular, lightweight project:

- **[`polity4j-core`](./polity4j-core)**: Core pipelines, request/response models, and custom exceptions.
- **[`polity4j-reliability`](./polity4j-reliability)**: Pluggable resiliency: `RetryModule`, `CircuitBreakerModule`, and `FallbackChainModule`.
- **[`polity4j-cost`](./polity4j-cost)**: Cost optimization: `BudgetGuardrailModule`, `ExactCacheModule`, and `ModelRouterModule`.
- **[`polity4j-quality`](./polity4j-quality)**: Quality control: `PromptOptimizerModule` and `ResponseValidatorModule`.
- **[`polity4j-examples`](./polity4j-examples)**: Executable reliability pipeline demo.

---

## Installation

Add dependencies to your `pom.xml`:

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
<dependency>
  <groupId>io.polity4j</groupId>
  <artifactId>polity4j-cost</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.polity4j</groupId>
  <artifactId>polity4j-quality</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Quick Start

### 1. Implement `LlmClient`
Adapt any provider client:

```java
public class MyCustomClient implements LlmClient {
    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        // Execute raw HTTP or SDK call
        return LlmResponse.builder("response content", request.model(), provider()).build();
    }

    @Override
    public String provider() { return "custom-provider"; }
}
```

### 2. Configure a Production-Grade Pipeline
Combine quality, cost, and reliability rules:

```java
LlmClient primaryClient = new MyCustomClient();
LlmClient fallbackClient = new AnotherClient();

RoutingPolicy routingPolicy = RoutingPolicy.builder()
    .threshold(0.5)
    .cheapModel("claude-3-haiku-20240307")
    .expensiveModel("claude-3-5-sonnet-20241022")
    .build();

LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
    // 1. Quality: Optimize whitespaces and keep latest history
    .with(new PromptOptimizerModule(PromptOptimizerConfig.DEFAULT))
    // 2. Cost: Route simple requests to Claude Haiku
    .with(new ModelRouterModule(routingPolicy))
    // 3. Cost: Short-circuit identical requests
    .with(new ExactCacheModule())
    // 4. Reliability: Retry transient API rate limits
    .with(new RetryModule(RetryConfig.DEFAULT))
    // 5. Reliability: Block outbound calls if provider remains offline
    .with(new CircuitBreakerModule("primary-provider", CircuitBreakerConfig.DEFAULT))
    // 6. Reliability: Failover to secondary client if primary is exhausted
    .with(new FallbackChainModule(List.of(fallbackClient)))
    .build();
```

### 3. Execute Requests
Send requests safely:

```java
LlmRequest request = LlmRequest.builder(
    "Explain quantum computing in one sentence.", 
    "claude-3-5-sonnet-20241022")
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

Polity4j is licensed under the [Apache License, Version 2.0](./LICENSE).
