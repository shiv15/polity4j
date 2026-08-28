# Spike Findings: Benchmarking Retry & Backoff Across Polity4j, Resilience4j, and LangChain4j

## Executive Summary

This spike evaluates whether a single common interface (e.g., `maxAttempts`, `backoffMs`, `jitter`, `retryPredicate`, `fallback`) can represent "retry with backoff" across **Polity4j**, **Resilience4j**, and **LangChain4j** without leaking library-specific quirks or breaking abstractions.

---

## 1. Library API & Architectural Inspection

### A. Polity4j (`polity4j-reliability`)
- **Public API**: `io.polity4j.reliability.RetryModule` implementing `PipelineModule` (`LlmResponse process(LlmRequest request, PipelineChain next)`). Configured via `io.polity4j.reliability.RetryConfig`.
- **Max Attempts**: `maxAttempts` (default: 3).
- **Backoff Strategy**: Exponential backoff calculated as `initialDelay * (multiplier ^ (attempt - 1))` capped at `maxDelay`. Also supports `RateLimitException.retryAfterMs()` overrides provided directly by LLM API headers.
- **Jitter**: None natively built into `RetryConfig`.
- **Exception Predicate**: Hardcoded selective retry in `RetryModule.process(...)`. Retries `RateLimitException` (always by default) and `OverloadedException` (if `retryOnOverloaded(true)`). Does **not** retry generic `RuntimeException`, `ModelUnavailableException`, or non-`PolityException` instances.
- **Execution Model**: Synchronous, thread-blocking (`Thread.sleep`). Pipeline design specifically bound to `LlmRequest` / `LlmResponse`.

### B. Resilience4j (`resilience4j-retry`)
- **Public API**: `io.github.resilience4j.retry.Retry`, `RetryConfig`, and decorator methods (`Retry.decorateSupplier`, `Retry.decorateCallable`, `Retry.decorateCompletionStage`).
- **Max Attempts**: `maxAttempts` (default: 3).
- **Backoff Strategy**: Highly configurable via `IntervalFunction` (fixed delay, exponential backoff, randomized backoff, custom functions).
- **Jitter**: Out-of-the-box support via `IntervalFunction.ofExponentialRandomBackoff(...)` and `IntervalFunction.ofRandomized(...)`.
- **Exception Predicate**: Fully customizable via `retryOnException(Predicate<Throwable>)`, `retryExceptions(...)`, `ignoreExceptions(...)`, and `retryOnResult(Predicate<T>)`.
- **Execution Model**: Generic higher-order wrapper over any `Supplier<T>`, `Callable<T>`, or `CompletionStage<T>`. Supports both sync and async execution.

### C. LangChain4j (`langchain4j` / `langchain4j-open-ai`)
- **Public API**: No standalone Retry module or interface. Exposed only as internal knobs on specific model client builders (e.g. `OpenAiChatModel.builder().maxRetries(3)`).
- **Max Attempts**: `maxRetries` (integer property on supported model builders).
- **Backoff Strategy**: Internal to transport layer (OkHttpClient interceptor / Retrofit retry loop).
- **Jitter**: Not configurable; managed internally by transport.
- **Exception Predicate**: Hardcoded inside internal HTTP client logic (retries transient HTTP 429/5xx and network I/O errors).
- **Execution Model**: Embedded inside model invocation calls (`model.generate(...)`). Cannot be instantiated as an isolated component or wrapped around arbitrary code.

---

## 2. Experimental Spike Benchmark Results

The spike runner (`io.polity4j.scratch.RetrySpike`) tested a simulated operation configured to retry up to 3 times with a 100ms delay:

| Library | Exception Type Thrown | Total Attempts | Final Result | Elapsed Time | Succeeded? | Key Behavior Observed |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Polity4j** | `RateLimitException` | 3 | `Polity4j Success` | ~315 ms | **Yes** | Slept 100ms between attempt 1->2 and 2->3, succeeded on attempt 3. |
| **Polity4j** | `RuntimeException` | 1 | `RuntimeException` | ~4 ms | **No** | Failed immediately on attempt 1 because `RetryModule` ignores non-Polity domain rate limit exceptions. |
| **Resilience4j** | `RuntimeException` | 3 | `Resilience4j Success` | ~213 ms | **Yes** | Wrapped supplier cleanly, retried twice with 100ms delay, succeeded on attempt 3. |
| **LangChain4j** | N/A (Inspected) | N/A | Builder Inspection | N/A | N/A | Retries are internal to HTTP client layer; cannot wrap arbitrary `Supplier`/`Callable`. |

---

## 3. Shape Mismatches & Technical Impedance

1. **Target Abstraction Mismatch (Generic Operation vs Domain Pipeline)**:
   - Resilience4j operates on generic Java functional interfaces (`Supplier<T>`, `Callable<T>`).
   - Polity4j operates as an `LlmRequest` $\rightarrow$ `LlmResponse` pipeline module.
   - LangChain4j does not expose a callable wrapper; retry is an internal implementation detail of model invocation.

2. **Exception Predicate Model**:
   - Resilience4j permits arbitrary exception filtering.
   - Polity4j hardcodes domain-specific exceptions (`RateLimitException`, `OverloadedException`) and intentionally bypasses retries for server failure/unavailability (`ModelUnavailableException`).
   - LangChain4j relies on internal HTTP status codes and OkHttpClient I/O exception classifiers.

3. **Backoff and Jitter Control**:
   - Resilience4j supports explicit jitter functions out of the box.
   - Polity4j supports exponential multiplier and provider `retry-after` header parsing, but lacks explicit jitter settings in `RetryConfig`.
   - LangChain4j offers zero control over backoff curves or jitter.

---

## 4. Architectural Recommendation

### **Recommendation: Per-Library Special-Casing (Adapter Strategy)**

A single naive common retry interface (e.g., `RetryAdapter.execute(Supplier<T>)`) **cannot** cleanly represent all three libraries without leaking quirks or forcing invalid abstractions:

1. **LangChain4j** cannot fit a generic `RetryAdapter` wrapper because its retry logic lives exclusively inside the HTTP transport tier of model client instances. To benchmark LangChain4j retry, the benchmark harness must construct the model client with `maxRetries(n)` and invoke the model API directly.
2. **Polity4j**'s `RetryModule` requires `LlmRequest` / `PipelineChain` and selectively retries domain exceptions (`RateLimitException`). Forcing it into a generic `Supplier<T>` wrapper bypasses its core pipeline contract.
3. **Resilience4j** is the only library that natively functions as an isolated general-purpose retry decorator.

### Proposed Benchmark Design Pattern
Rather than attempting a unified `RetryAdapter` interface for execution, the benchmark suite should separate:
- **Library Native Harnesses**: Each benchmark implementation configures retry using the library's native approach (Polity4j via `PipelineChain`, Resilience4j via `Retry.decorateSupplier`, LangChain4j via `OpenAiChatModel.builder().maxRetries(...)`).
- **Unified Benchmark Measurement Layer**: Capture metrics (total attempts, latency, success/failure rate) at the benchmark harness boundary.
