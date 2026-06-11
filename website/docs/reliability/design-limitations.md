---
sidebar_position: 4
---

# Design Limitations & Decisions

Polity is designed as a lightweight, embeddable Java library. To keep the footprint small, dependencies zero, and implementation simple, certain reliability primitives make intentional tradeoffs.

---

## 1. Thread-Blocking Sleeps in Retries

The `RetryModule` manages backoff delay durations by calling `Thread.sleep(long)`. 

### Why this tradeoff was made
The core pipeline flow (`PipelineModule.process`) is fully synchronous:
```java
LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException;
```
Because of this synchronous contract, non-blocking asynchronous futures cannot be easily waited on without blocking the calling thread anyway. Rather than introducing complex asynchronous reactive wrappers or thread pools, Polity blocks the calling thread during retries.

### Mitigation
If you are running Polity in a high-throughput, reactive, or servlet-based server environment, **use Java 21+ Virtual Threads**. 
Virtual threads are extremely lightweight. When a virtual thread calls `Thread.sleep()`, the JVM suspends the virtual thread and yields the underlying platform thread to execute other tasks. This eliminates the scalability penalties typically associated with blocking platform threads.

---

## 2. In-Memory Circuit Breaker Scope

The `CircuitBreakerModule` tracks error rates, request volumes, and states (Closed, Open, Half-Open) fully **in-memory** per pipeline instance.

### Why this tradeoff was made
Distributed circuit breakers require a shared state repository (such as Redis, Consul, or database entries) to coordinate state across multiple instances of an application. 
Adding distributed state synchronization directly to the library would require:
- Bringing in third-party client dependencies (e.g. Jedis/Lettuce for Redis).
- Exposing complex configuration schemas for cache clustering and connection management.
- Overcomplicating a library whose goal is to be a lightweight core client wrapper.

### Distributed Environments
If you run multiple instances of a service using Polity:
- **Instance Isolation**: Instance A may trip its circuit breaker due to errors, while Instance B keeps calling the provider. This is often desirable, as it prevents local network issues on Instance A from affecting Instance B.
- **Enterprise / SaaS Layer**: If you require strict, globally coordinated circuit breaking across a cluster, this logic is best implemented in the SaaS or API gateway layer (e.g., Kong, Apigee, Envoy sidecars) rather than inside the application code.
