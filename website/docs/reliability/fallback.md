---
sidebar_position: 3
---

# Fallback Chain Module

The `FallbackChainModule` completes the reliability pipeline by routing requests to alternative model configurations or providers when your primary endpoint fails.

---

## Behavior

- **Fallback-Eligible Exceptions**: The fallback chain is triggered when a provider error occurs:
  - `ModelUnavailableException` (e.g. server down or circuit breaker open)
  - `OverloadedException` (server saturated)
  - `RateLimitException` (rate limited and retries exhausted)
- **Immediate Propagation**: App-side errors (such as `BudgetExceededException` or `ContextOverflowException`) propagate immediately and will not trigger fallback clients.
- **Cause Chaining**: When all fallback options are exhausted, the module throws a final `ModelUnavailableException` with the message `all-providers-exhausted`. It nestedly chains the causes of every single failure together using `initCause()` so you can diagnose the root cause of every tried client.

---

## Code Example

In this example, the pipeline will try to resolve the request using OpenAI first. If it encounters a provider outage or trips the circuit breaker, the fallback module intercepts the exception and redirects the call to Claude (Anthropic), and eventually to a local Llama3 instance (Ollama) if Claude is also unreachable.

```java
import io.polity4j.core.LlmPipeline;
import io.polity4j.reliability.fallback.FallbackChainModule;
import java.util.List;

// 1. Instantiate alternative client adapters
LlmClient primaryOpenAi = new OpenAiClient(apiKey);
LlmClient fallbackClaude = new AnthropicLlmClient(apiKey, "claude-3-haiku-20240307");
LlmClient localOllama = new OllamaClient("localhost:11434");

// 2. Wrap alternative backup clients in the fallback module
FallbackChainModule fallbackModule = new FallbackChainModule(
    List.of(fallbackClaude, localOllama)
);

// 3. Configure primary client and add fallback support to the pipeline
LlmPipeline pipeline = LlmPipeline.builder(primaryOpenAi)
    .with(fallbackModule)
    .build();
```
