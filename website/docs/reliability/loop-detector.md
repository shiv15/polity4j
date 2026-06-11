---
sidebar_position: 3
---

# Agent Loop Detector Module

The `AgentLoopDetectorModule` protects your application from runaway or recursive LLM execution loops (often caused by agent tool-use loops or self-correction cycles).

---

## Behavior

- **Session Identification**: Tracks requests per `callerId` (session identifier). Requests without a `callerId` bypass loop detection.
- **Request Frequency Limit**: Limits the number of requests a single `callerId` can make within a sliding time window (e.g., maximum 10 requests per minute).
- **Consecutive Duplicate Limit**: Limits the number of times the exact same prompt can be sent consecutively by a `callerId` (e.g., maximum 3 identical prompts), stopping agents from getting stuck in repetitive correction loops.
- **Exception**: Throws `AgentLoopException` (HTTP status code 422 - Unprocessable Entity) when a loop is detected.

---

## Configuration Options

Configure `AgentLoopDetectorModule` using `AgentLoopConfig.Builder`:

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `maxRequestsPerSession` | `int` | `10` | Maximum requests allowed in the sliding window. |
| `maxConsecutiveDuplicates` | `int` | `3` | Maximum consecutive identical prompts allowed. |
| `slidingWindowMs` | `long` | `60000ms` (1 min) | Duration of the sliding frequency window. |

---

## Code Example

```java
import io.polity4j.reliability.loop.AgentLoopConfig;
import io.polity4j.reliability.loop.AgentLoopDetectorModule;

// 1. Configure custom thresholds
AgentLoopConfig config = AgentLoopConfig.builder()
    .maxRequestsPerSession(15)
    .maxConsecutiveDuplicates(2)
    .slidingWindowMs(30_000) // 30 seconds
    .build();

// 2. Instantiate the module
AgentLoopDetectorModule loopDetector = new AgentLoopDetectorModule(config);

// 3. Register with your pipeline
LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
    .with(loopDetector)
    .build();

// 4. Execute requests specifying the callerId
LlmRequest request = LlmRequest.builder("Identify the bugs in this code.", "gpt-4o")
    .callerId("session-abc-123")
    .build();

LlmResponse response = pipeline.execute(request);
```
