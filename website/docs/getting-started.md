---
sidebar_position: 2
---

# Getting Started

Learn how to install and configure Polity4j in your Java project.

---

## Prerequisites

- **Java Version**: JDK 17 or higher
- **Build Tool**: Maven 3.8+ or Gradle 7+

---

## Installation

Add the following coordinates to your build configuration.

### Maven

Add these dependencies to your `pom.xml`:

```xml
<dependency>
    <groupId>io.polity4j</groupId>
    <artifactId>polity4j-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.polity4j</groupId>
    <artifactId>polity4j-reliability</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Simple Setup Guide

### Step 1: Implement `LlmClient`
Polity4j decouples your HTTP engine from the core logic. Implement `LlmClient` to write a small adapter using your favorite library (e.g. Java `HttpClient`, OkHttp, or an SDK):

```java
import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.exception.PolityException;

public class MyLlmClient implements LlmClient {
    @Override
    public LlmResponse call(LlmRequest request) throws PolityException {
        // Send request using your HTTP client...
        // Map any HTTP errors/rate limits to PolityException subtypes
        return LlmResponse.builder("Model text response", request.model(), provider())
            .build();
    }

    @Override
    public String provider() {
        return "my-provider";
    }
}
```

### Step 2: Build a Resilient Pipeline
Initialize your clients and register the desired reliability modules:

```java
import io.polity4j.core.LlmPipeline;
import io.polity4j.reliability.RetryConfig;
import io.polity4j.reliability.RetryModule;

LlmClient myClient = new MyLlmClient();

LlmPipeline pipeline = LlmPipeline.builder(myClient)
    // Add retry support (default: 3 attempts, exponential backoff)
    .with(new RetryModule(RetryConfig.DEFAULT))
    .build();
```

### Step 3: Execute a Request
Construct an `LlmRequest` and run it through the pipeline:

```java
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;

LlmRequest request = LlmRequest.builder("What is the speed of light?", "my-model")
    .maxTokens(100)
    .build();

try {
    LlmResponse response = pipeline.execute(request);
    System.out.println("Answer: " + response.content());
} catch (PolityException e) {
    System.err.println("Pipeline failed: " + e.getMessage());
}
```
