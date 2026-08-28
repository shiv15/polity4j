# Harness Spike Findings: Fault Injection & Per-Library Native Retry Benchmark

## Executive Summary

Following the initial findings in `scratch/FINDINGS.md`, we abandoned the unified `ResiliencePolicy` interface approach. Instead, we built a **fault-injection harness** and **per-library native retry harnesses** for **Polity4j**, **Resilience4j**, and **LangChain4j**.

Rather than forcing libraries into a shared wrapper interface, commonality is established at the **fault boundary** via an `AttemptRecorder` listening to backend executions.

---

## 1. Architecture & Components Built

```
                  +-----------------------------------+
                  |           FaultProfile            |
                  | (Seeded / Explicit Fault Sequence)|
                  +-----------------+-----------------+
                                    |
          +-------------------------+-------------------------+
          |                                                   |
+---------v----------+                             +----------v---------+
| InProcessFakeBackend|                             |   WireMockBackend  |
|  (Supplier/Chain)  |                             | (OpenAI HTTP 429/  |
+---------+----------+                             |  503/500/400/200)  |
          |                                        +----------+---------+
          |                                                   |
          +-------------------------+-------------------------+
                                    |
                         +----------v----------+
                         |   AttemptRecorder   |
                         |  (Recorded History) |
                         +---------------------+
```

### A. Semantic Fault Vocabulary & Sequence (`FaultType`, `FaultProfile`)
- **`FaultType`**: `RATE_LIMITED`, `OVERLOADED`, `TRANSIENT_5XX`, `PERMANENT_4XX`, `MALFORMED_RESPONSE`, `SUCCESS`.
- **`FaultProfile`**: Guarantees identical fault sequences per attempt across all libraries under test (via explicit array or seeded RNG).

### B. Common Measurement Layer (`AttemptRecord`, `AttemptRecorder`)
- Sits at the backend/mock boundary. Libraries are unaware of its existence.
- On every call, logs: `attemptNumber` (1-indexed), `timestampMs`, `FaultType`, and `isTerminal`.

### C. Backend Implementations
1. **`InProcessFakeBackend`**: Plain `Supplier<String>` and `PipelineChain`. Maps fault types to Polity4j exceptions (`RateLimitException`, `OverloadedException`, `ModelUnavailableException`, generic `RuntimeException`).
2. **`WireMockBackend`**: OpenAI-compatible HTTP server running on a dynamic local port. Uses WireMock `ResponseTransformer` to serve exact HTTP status codes and JSON payloads (429, 503, 500, 400, malformed JSON, 200 OK).

### D. Canonical Normalized Backoff Curve
To eliminate backoff configuration mismatch:
- **Canonical Parameters**: `initialDelay = 100ms`, `multiplier = 2.0`, `maxDelay = 2000ms`, `maxAttempts = 3`, `no jitter`.
- **Polity4j**: Configured via `RetryConfig.builder().maxAttempts(3).initialDelay(Duration.ofMillis(100)).multiplier(2.0).maxDelay(Duration.ofMillis(2000)).build()`.
- **Resilience4j**: Configured via `IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2.0, Duration.ofMillis(2000))`.
- **LangChain4j**: Configured via `OpenAiChatModel.builder().maxRetries(3)`. *Note*: Internal backoff delays are baked into OkHttpClient/Retrofit interceptors and cannot be customized to 100ms. This asymmetry is recorded as an inherent library characteristic.

### E. Native Library Harnesses
- **`PolityHarness`**: Runs `RetryModule` standalone over a `PipelineChain`.
- **`Resilience4jHarness`**: Decorates suppliers using `Retry.decorateSupplier` with `retryOnException(e -> true)` for unconstrained exception matching.
- **`LangChain4jHarness`**: Instantiates `OpenAiChatModel` pointing to `WireMockBackend` URL and invokes `model.generate(...)`.

---

## 2. Contract Test Results (`HarnessContractTest`)

### Contract Test 6a: "Transient-only Parity"
- **Fault Profile Sequence**: `RATE_LIMITED` $\rightarrow$ `RATE_LIMITED` $\rightarrow$ `SUCCESS`
- **Goal**: Verify all three libraries successfully recover when encountering only transient rate limit failures.

| Library | Execution Transport | Total Attempts Made | Final Result | Succeeded? |
| :--- | :--- | :--- | :--- | :--- |
| **Polity4j** | In-Process Fake | **3** | `InProcess Success` | **Yes** |
| **Polity4j** | WireMock HTTP | **3** | `WireMock OpenAI Success` | **Yes** |
| **Resilience4j** | In-Process Fake | **3** | `InProcess Success` | **Yes** |
| **Resilience4j** | WireMock HTTP | **3** | `WireMock OpenAI Success` | **Yes** |
| **LangChain4j** | WireMock HTTP | **3** | `WireMock OpenAI Success` | **Yes** |

*All 5 test variants made exactly 3 attempts and succeeded.*

---

### Contract Test 6b: "Mixed Transient + Permanent"
- **Fault Profile Sequence**: `RATE_LIMITED` $\rightarrow$ `PERMANENT_4XX` $\rightarrow$ `SUCCESS`
- **Goal**: Surface the behavioral difference between Polity4j's domain-selective retry vs Resilience4j's generic catch-all retry.

| Library | Exception Filter Setting | Attempt 1 | Attempt 2 | Attempt 3 | Total Attempts Made | Final Outcome |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Polity4j** | Selective (`RateLimit`, `Overloaded`) | `RATE_LIMITED` | `PERMANENT_4XX` (Throws `RuntimeException`) | *Not executed* | **2** | **Failed (Stopped on Attempt 2)** |
| **Resilience4j** | Generic (`retryOnException(e -> true)`) | `RATE_LIMITED` | `PERMANENT_4XX` (Retried) | `SUCCESS` | **3** | **Succeeded (Attempt 3)** |

#### Key Takeaway & Metric Demonstration:
- **Polity4j** stopped retrying immediately upon encountering `PERMANENT_4XX` (attempt 2), propagating the error without wasting attempts.
- **Resilience4j** (when configured to retry all exceptions) blindly retried `PERMANENT_4XX` and reached attempt 3 (`SUCCESS`).
- This illustrates **retry precision**: selective retry avoids wasted backend calls on non-retryable errors.

---

## 3. Further API Surprises & Insights

1. **Polity4j Exception Constructors**:
   - `ResponseValidationException` requires a 2-arg constructor `(message, validationDetails)`.
   - `RetryModule`'s `process(...)` method catches specific `PolityException` subtypes (`RateLimitException`, `OverloadedException`) and bypasses retries for non-retryable errors (`ModelUnavailableException` or plain `RuntimeException`).

2. **WireMock 3.x Extensions**:
   - WireMock response transformation is registered via `ResponseTransformer` and attached to stubs via `.withTransformers("fault-injector")`.

3. **LangChain4j Transport Isolation**:
   - LangChain4j can only execute against HTTP backends (`WireMockBackend`). Attempting to test it against an in-process fake is impossible because it does not decouple transport logic from model execution.

---

## 4. Conclusion & Next Steps

The fault-injection harness and native library harnesses are fully implemented and verified via contract tests. The benchmark architecture successfully measures attempt counts and behavior at the fault boundary (`AttemptRecorder`).
