package io.polity4j.integrations.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.cost.cache.ExactCacheModule;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerConfig;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Polity4jMeterBinderTest {

    private static final String MODEL = "claude-3-5-sonnet-20241022";

    private MeterRegistry registry;
    private ExactCacheModule cacheModule;
    private CircuitBreakerModule circuitBreaker;
    private Polity4jMeterBinder binder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        cacheModule = new ExactCacheModule();
        circuitBreaker = new CircuitBreakerModule("anthropic",
                CircuitBreakerConfig.builder()
                        .failureThreshold(3)
                        .cooldownDuration(Duration.ofSeconds(30))
                        .build());
        binder = new Polity4jMeterBinder(cacheModule,
                List.of(circuitBreaker));
        binder.bindTo(registry);
    }

    private LlmRequest request(String prompt) {
        return LlmRequest.builder(prompt, MODEL).build();
    }

    private LlmResponse ok() {
        return LlmResponse.builder("ok", MODEL, "anthropic")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    // ------------------------------------------------------------------
    // Cache metrics
    // ------------------------------------------------------------------

    @Test
    void exportsCacheHits() throws Exception {
        // One miss then one hit
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("hello"), req -> ok());

        double hits = registry.get("polity4j.cache.hits")
                .gauge().value();
        assertThat(hits).isEqualTo(1.0);
    }

    @Test
    void exportsCacheMisses() throws Exception {
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("goodbye"), req -> ok());

        double misses = registry.get("polity4j.cache.misses")
                .gauge().value();
        assertThat(misses).isEqualTo(2.0);
    }

    @Test
    void exportsCacheHitRate() throws Exception {
        // 1 miss then 3 hits = 75% hit rate
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("hello"), req -> ok());

        double hitRate = registry.get("polity4j.cache.hit.rate")
                .gauge().value();
        assertThat(hitRate).isEqualTo(0.75);
    }

    @Test
    void exportsCacheSize() throws Exception {
        cacheModule.process(request("hello"), req -> ok());
        cacheModule.process(request("goodbye"), req -> ok());

        double size = registry.get("polity4j.cache.size")
                .gauge().value();
        assertThat(size).isEqualTo(2.0);
    }

    // ------------------------------------------------------------------
    // Circuit breaker metrics
    // ------------------------------------------------------------------

    @Test
    void exportsCircuitStateClosedAsZero() {
        double state = registry.get("polity4j.circuit.state")
                .tag("provider", "anthropic")
                .gauge().value();
        assertThat(state).isEqualTo(0.0); // CLOSED
    }

    @Test
    void exportsCircuitStateOpenAsTwo() throws Exception {
        // Trip the circuit
        var failing = (io.polity4j.core.PipelineChain) req -> {
            throw new io.polity4j.core.exception.OverloadedException("anthropic");
        };

        for (int i = 0; i < 3; i++) {
            try { circuitBreaker.process(request("p" + i), failing); }
            catch (Exception ignored) {}
        }

        double state = registry.get("polity4j.circuit.state")
                .tag("provider", "anthropic")
                .gauge().value();
        assertThat(state).isEqualTo(2.0); // OPEN
    }

    @Test
    void tagsCircuitStateWithProvider() {
        assertThat(registry.get("polity4j.circuit.state")
                .tag("provider", "anthropic")
                .gauge()).isNotNull();
    }
}
