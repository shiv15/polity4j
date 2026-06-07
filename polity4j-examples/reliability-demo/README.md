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

## Swapping providers

To point this at a different provider, replace `AnthropicLlmClient`
with your own `LlmClient` implementation. The pipeline modules —
retry, circuit breaker, fallback — work identically regardless of
which provider is underneath.
