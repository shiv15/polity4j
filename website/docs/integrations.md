# Framework Integrations

Polity4j provides out-of-the-box integrations for modern Java application stacks, starting with **Spring Boot 3** and **Micrometer**.

These modules are located inside the `polity4j-integrations` parent module.

---

## 1. Spring Boot 3 Integration

Importing `polity4j-integrations-spring-boot3` enables autoconfiguration of your Polity4j pipeline and provides annotation-driven execution (AOP).

### Autoconfiguration
The autoconfiguration (`Polity4jAutoConfiguration`) automatically builds your pipeline:
1. **Discovers all `PipelineModule` beans** present in the Spring application context.
2. **Registers an aspect** that intercepts and guards annotated methods.

To use it, simply define your `LlmClient` and any required pipeline modules as beans:

```java
@Configuration
public class PolityConfig {

    @Bean
    public LlmClient llmClient() {
        return new OpenAiAdapter(System.getenv("OPENAI_API_KEY"));
    }

    @Bean
    public RetryModule retryModule() {
        return new RetryModule(RetryConfig.DEFAULT);
    }

    @Bean
    public CircuitBreakerModule circuitBreaker() {
        return new CircuitBreakerModule("openai", CircuitBreakerConfig.DEFAULT);
    }
}
```

### The `@Polity4jGuarded` Annotation
You can mark any service method accepting a single `LlmRequest` and returning a `LlmResponse` as guarded. The AOP aspect intercepts the execution and routes the request through the `LlmPipeline` automatically:

```java
@Service
public class AiService {

    @Polity4jGuarded
    public LlmResponse ask(LlmRequest request) {
        // The method body is never called directly.
        // It is replaced by aspect advice which executes the pipeline.
        return null; 
    }
}
```

---

## 2. Micrometer Integration

Importing `polity4j-integrations-micrometer` exposes operational metrics of your Polity4j modules to a Micrometer `MeterRegistry` (e.g. for Prometheus, Datadog).

### Metrics Exported

| Metric Name | Type | Description |
| --- | --- | --- |
| `polity4j.cache.hits` | `Gauge` | Total exact cache hit count |
| `polity4j.cache.misses` | `Gauge` | Total exact cache miss count |
| `polity4j.cache.hit.rate` | `Gauge` | Exact cache hit rate (0.0 to 1.0) |
| `polity4j.cache.size` | `Gauge` | Current number of entries in the exact cache |
| `polity4j.circuit.state` | `Gauge` | Circuit state tagged by provider: `0`=CLOSED, `1`=HALF_OPEN, `2`=OPEN |

### Configuration with Spring Boot

Register `Polity4jMeterBinder` as a Spring bean:

```java
@Bean
public Polity4jMeterBinder polity4jMeterBinder(
        ExactCacheModule cache,
        List<CircuitBreakerModule> circuitBreakers) {
    return new Polity4jMeterBinder(cache, circuitBreakers);
}
```

Micrometer registries will automatically bind these gauges.
