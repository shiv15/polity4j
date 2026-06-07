# Polity4j Examples

Runnable examples demonstrating Polity4j modules against real AI providers.
Each example is a standalone Maven module with its own README.

## Examples

| Module | What it demonstrates |
|---|---|
| [reliability-demo](./reliability-demo) | Retry, circuit breaker, and fallback chain wired into a real pipeline |

## Prerequisites

- Java 17+
- Maven 3.8+
- API key for the provider used in the example (see each example's README)

## Building all examples

From the repo root:

```bash
mvn clean compile -pl polity4j-examples/reliability-demo \
    -am
```
