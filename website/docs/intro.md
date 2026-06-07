---
sidebar_position: 1
---

# Introduction to Polity4j

Polity4j is a lightweight, zero-dependency reliability orchestration library for LLM integrations in Java 17+.

When building AI-powered features, developers often implement rate limiting, backoff, retries, and fallback strategies directly inside their business logic or API clients. This tightly couples LLM operations to reliability concerns, leading to repetitive code and poor maintainability.

Polity4j solves this problem by introducing a clean **Chain-of-Responsibility** pipeline pattern that lets you configure reliability features declaratively outside your client adapters.

---

## Design Principles

- **Zero Dependency Core**: The core module contains absolutely no dependencies outside of unit testing libraries. It will not conflict with your existing libraries, frameworks, or runtime environments.
- **Stateless Where Possible**: Pipeline modules are designed to be stateless and reusable. Stateful components (such as caches or circuit breakers) are implemented with thread safety in mind.
- **Permissive and permissive API**: Built using Java 17 record structures for immutability and builders for clean, expressive configurations.

---

## How It Works

```
                        LlmRequest
                            ↓
                    +---------------+
                    |  LlmPipeline  |
                    +---------------+
                            ↓
                  [ Pipeline Chain ]
                            ↓
                 +---------------------+
                 |  RetryModule        |
                 +---------------------+
                            ↓
                 +---------------------+
                 |  CircuitBreaker     |
                 +---------------------+
                            ↓
                 +---------------------+
                 |  FallbackChain      |
                 +---------------------+
                            ↓
                  +-------------------+
                  |     LlmClient     |  ← (Your custom client adapter)
                  +-------------------+
```

1. **`LlmRequest`**: Immutable value record capturing the prompt, model, max tokens, history, and caller metadata.
2. **`LlmPipeline`**: Orchestrator that wires the request through a list of ordered pipeline modules.
3. **`PipelineModule`**: A single step in the pipeline. It can intercept, modify, validate, or short-circuit requests/responses.
4. **`LlmClient`**: The single boundary adapter. You write a minimal adapter implementing this interface to wrap your preferred HTTP client or vendor SDK.
