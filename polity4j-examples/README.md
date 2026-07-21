# Polity4j Examples

Runnable examples demonstrating Polity4j modules against real AI providers or simulated clients.
Each example is a standalone Maven module.

## Examples

| Module | What it demonstrates |
|---|---|
| [reliability-demo](./reliability-demo) | Retry, circuit breaker, and fallback chain wired into a real pipeline |
| [cost-demo](./cost-demo) | Exact caching (0ms/$0), model routing (Haiku vs Sonnet), and budget guardrails |
| [quality-demo](./quality-demo) | Prompt optimization, context window management, and response validation with corrective retries |
| [adapters-demo](./adapters-demo) | Using built-in AnthropicAdapter and OpenAiAdapter with JDK 11 HttpClient and Jackson |
| [full-pipeline-demo](./full-pipeline-demo) | Complete end-to-end production pipeline combining Quality, Cost, Reliability, Adapters, and Micrometer metrics |

## Prerequisites

- Java 17+
- Maven 3.8+
- Optional: API keys (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) for running against live LLM endpoints. All demos run automatically with simulated clients if keys are omitted.

## Running Examples

From the project root, execute any demo via `exec-maven-plugin`:

```bash
# Reliability Demo
mvn exec:java -pl polity4j-examples/reliability-demo

# Cost Optimization Demo
mvn exec:java -pl polity4j-examples/cost-demo

# Quality Control Demo
mvn exec:java -pl polity4j-examples/quality-demo

# Adapters Demo
mvn exec:java -pl polity4j-examples/adapters-demo

# Full Production Pipeline Demo
mvn exec:java -pl polity4j-examples/full-pipeline-demo
```
