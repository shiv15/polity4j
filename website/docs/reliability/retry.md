---
sidebar_position: 1
---

# Retry Module

The `RetryModule` handles transient errors by automatically retrying failed LLM requests with exponential backoff.

---

## Behavior

- **Eligible Exceptions**: The module automatically retries on:
  - `RateLimitException` (HTTP 429)
  - `OverloadedException` (HTTP 529)
- **Immediate Propagation**: Non-transient exceptions (such as `BudgetExceededException` or `ContextOverflowException`) bypass retries and are thrown immediately to avoid wasteful API calls.
- **Rate Limit Headers**: If a `RateLimitException` contains a `retry-after` header value, the module will respect that duration and sleep exactly that long before trying again.

---

## Configuration Options

Configure `RetryModule` behavior using `RetryConfig.Builder`:

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `maxAttempts` | `int` | `3` | Maximum number of execution attempts. |
| `initialDelay` | `Duration` | `500ms` | Initial sleep delay before the first retry. |
| `maxDelay` | `Duration` | `30s` | Maximum delay duration cap. |
| `multiplier` | `double` | `2.0` | Exponential multiplier factor (e.g. 500ms -> 1000ms -> 2000ms). |
| `retryOnRateLimit` | `boolean` | `true` | Whether to retry when rate limited. |
| `retryOnOverloaded` | `boolean` | `true` | Whether to retry when provider is overloaded. |

---

## Code Example

```java
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;
import java.time.Duration;

// 1. Build a custom retry configuration
RetryConfig config = RetryConfig.builder()
    .maxAttempts(5)
    .initialDelay(Duration.ofMillis(200))
    .maxDelay(Duration.ofSeconds(10))
    .multiplier(1.5)
    .build();

// 2. Wrap it in a RetryModule
RetryModule retryModule = new RetryModule(config);

// 3. Register with your pipeline
LlmPipeline pipeline = LlmPipeline.builder(myClient)
    .with(retryModule)
    .build();
```
