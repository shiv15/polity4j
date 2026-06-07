---
sidebar_position: 2
---

# Circuit Breaker Module

The `CircuitBreakerModule` prevents cascading failures and preserves rate limits by immediately blocking downstream calls to a provider when sustained outages occur.

---

## State Machine Transitions

The circuit breaker transitions atomically between three states:

```
        +-----------------------------------+
        |                                   |
        |             +--------+            |
        |      +----->| CLOSED |<-----+     |
        |      |      +--------+      |     |
        |      |                      |     |
        |  Successes               Success  |
        |  Met                     Probe    |
        |      |                      |     |
        |      |                      |     |
        |  +-------+              +-----------+
        |  | OPEN  |------------->| HALF_OPEN |
        |  +-------+  Cooldown    +-----------+
        |             Expired
        +-----------------------------------+
```

1. **`CLOSED`**: All requests pass through. Failures are counted. If consecutive failures hit the `failureThreshold`, the state transitions to `OPEN`.
2. **`OPEN`**: All requests fail fast immediately, throwing `ModelUnavailableException`. This prevents calling overloaded servers and blocks workflow degradation. When the `cooldownDuration` expires, the state transitions to `HALF_OPEN`.
3. **`HALF_OPEN`**: The pipeline allows a restricted number of test requests (`successesRequiredToClose`). If any request fails during this probe state, the breaker trips back to `OPEN` and resets the cooldown timer. If all probe requests succeed, the breaker returns to `CLOSED`.

---

## Configuration Options

Configure `CircuitBreakerModule` behavior using `CircuitBreakerConfig.Builder`:

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `failureThreshold` | `int` | `5` | Consecutive failures required to trip the circuit open. |
| `cooldownDuration` | `Duration` | `30s` | Time duration to remain open before trying recovery. |
| `successesRequiredToClose` | `int` | `2` | Consecutive successful probe requests required to close the circuit. |

---

## Code Example

```java
import io.polity4j.reliability.circuitbreaker.CircuitBreakerConfig;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;
import java.time.Duration;

// 1. Build a custom circuit configuration
CircuitBreakerConfig config = CircuitBreakerConfig.builder()
    .failureThreshold(3)
    .cooldownDuration(Duration.ofSeconds(15))
    .successesRequiredToClose(1)
    .build();

// 2. Wrap it in a CircuitBreakerModule keyed to a specific provider
CircuitBreakerModule cbModule = new CircuitBreakerModule("openai", config);

// 3. Register with your pipeline
LlmPipeline pipeline = LlmPipeline.builder(myClient)
    .with(cbModule)
    .build();
```
