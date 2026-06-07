# Reliability Demo

Demonstrates a full Polity4j reliability pipeline making a real call
to the Anthropic API using Java's built-in HttpClient — no SDK required.

## What this shows

- `RetryModule` — retries transient failures with exponential backoff
- `CircuitBreakerModule` — trips open after sustained provider failures
- `FallbackChainModule` — routes to an alternative client when primary fails
- `AnthropicLlmClient` — a minimal adapter using plain Java HttpClient,
  showing how to implement `LlmClient` for any provider

## Pipeline structure

```
Your code
    ↓
RetryModule          ← retries on RateLimitException (up to 3x)
    ↓
CircuitBreakerModule ← trips open after 5 failures
    ↓
FallbackChainModule  ← tries fallback client if primary is unavailable
    ↓
AnthropicLlmClient   ← plain HttpClient → Anthropic Messages API
```

## Prerequisites

- Java 17+
- Maven 3.8+
- An Anthropic API key — get one at https://console.anthropic.com

## Setup

1. Clone the repo and build from root:

```bash
git clone <repo-url>
cd polity4j
mvn clean install -DskipTests
```

2. Set your API key as an environment variable:

```bash
# macOS / Linux
export ANTHROPIC_API_KEY=sk-ant-your-key-here

# Windows (Command Prompt)
set ANTHROPIC_API_KEY=sk-ant-your-key-here

# Windows (PowerShell)
$env:ANTHROPIC_API_KEY="sk-ant-your-key-here"
```

3. Run the demo:

```bash
mvn exec:java -pl polity4j-examples/reliability-demo
```

## Expected output

```
============================================================
Polity4j Reliability Demo
============================================================
Prompt : Explain in one sentence why reliability matters in distributed systems.
Model  : claude-3-5-sonnet-20241022
------------------------------------------------------------
Response  : Reliability matters because distributed systems...
Provider  : anthropic
Tokens    : 32 in / 41 out
Cost      : $0.000711
Latency   : 843ms
CB State  : CLOSED
============================================================
```

## Detailed Execution Walkthrough

When you execute `ReliabilityDemo.main`, the following sequential flow occurs:

1. **Initialization**:
   - The demo checks for the presence of the `ANTHROPIC_API_KEY` environment variable.
   - It instantiates two instances of `AnthropicLlmClient`:
     - **Primary Client**: Resolves requests using the default model `claude-3-5-sonnet-20241022` (configurable per request).
     - **Fallback Client**: Configured with a constructor override to use the cheaper, faster model `claude-3-haiku-20240307` if the primary client fails.

2. **Pipeline Construction**:
   - The reliability modules are chained together via the `LlmPipeline.Builder` in a specific order:
     - **RetryModule**: Retries transient blips (like rate limits or brief provider overloaded states) up to 3 times using exponential backoff (starting at 500ms and doubling with each attempt).
     - **CircuitBreakerModule**: Tracks consecutive failures for the `"anthropic"` provider. If 5 consecutive failures occur, the circuit transitions from `CLOSED` to `OPEN`, immediately blocking further calls to Anthropic for 30 seconds (failing fast with `ModelUnavailableException`).
     - **FallbackChainModule**: Intercepts provider-side exceptions (`ModelUnavailableException`, `OverloadedException`, `RateLimitException`). If the primary client fails (and retries are exhausted), the fallback module redirects the request to the `fallbackClient` using `claude-3-haiku-20240307`.

3. **Execution & Cost Estimation**:
   - The pipeline executes the prompt `"Explain in one sentence why reliability matters in distributed systems."`
   - The `AnthropicLlmClient` performs a raw POST request to `https://api.anthropic.com/v1/messages` using Java's built-in asynchronous HTTP client.
   - On a successful `200 OK` response, it parses the JSON response to extract the generated text content and token usage (`input_tokens` and `output_tokens`).
   - It approximates the call cost using Claude 3.5 Sonnet's model pricing ($3 per 1M input tokens and $15 per 1M output tokens).
   - Finally, the pipeline returns the populated `LlmResponse` and logs metrics such as total latency, estimated cost, token counts, and the current Circuit Breaker state (which remains `CLOSED` on success).

## Swapping providers

To point this at a different provider, replace `AnthropicLlmClient`
with your own `LlmClient` implementation. The pipeline modules —
retry, circuit breaker, fallback — work identically regardless of
which provider is underneath.
