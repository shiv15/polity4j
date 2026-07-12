package io.polity4j.integrations.micrometer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.polity4j.cost.cache.ExactCacheModule;
import io.polity4j.reliability.circuitbreaker.CircuitBreakerModule;
import io.polity4j.reliability.circuitbreaker.CircuitState;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Binds Polity4j module state to a Micrometer MeterRegistry.
 *
 * Metrics exported:
 *
 *   polity4j.cache.hits        (counter) — exact cache hit count
 *   polity4j.cache.misses      (counter) — exact cache miss count
 *   polity4j.cache.hit.rate    (gauge)   — rolling hit rate 0.0-1.0
 *   polity4j.cache.size        (gauge)   — current cache entry count
 *
 *   polity4j.circuit.state     (gauge)   — per provider:
 *                                          0=CLOSED, 1=HALF_OPEN, 2=OPEN
 *
 * Usage with Spring Boot:
 *
 *   @Bean
 *   public Polity4jMeterBinder polity4jMeterBinder(
 *           ExactCacheModule cache,
 *           List<CircuitBreakerModule> circuitBreakers) {
 *       return new Polity4jMeterBinder(cache, circuitBreakers);
 *   }
 *
 * The binder is discovered automatically by Spring Boot Actuator
 * if registered as a bean.
 */
public final class Polity4jMeterBinder implements MeterBinder {

    private final ExactCacheModule cacheModule;
    private final Collection<CircuitBreakerModule> circuitBreakers;

    public Polity4jMeterBinder(ExactCacheModule cacheModule,
                                Collection<CircuitBreakerModule> circuitBreakers) {
        this.cacheModule = Objects.requireNonNull(cacheModule, "cacheModule must not be null");
        this.circuitBreakers = Objects.requireNonNull(circuitBreakers, "circuitBreakers must not be null");
    }

    /** Convenience constructor for a single circuit breaker */
    public Polity4jMeterBinder(ExactCacheModule cacheModule,
                                CircuitBreakerModule circuitBreaker) {
        this(cacheModule, List.of(Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null")));
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        bindCacheMetrics(registry);
        bindCircuitBreakerMetrics(registry);
    }

    private void bindCacheMetrics(MeterRegistry registry) {
        Gauge.builder("polity4j.cache.hits",
                        cacheModule, ExactCacheModule::hits)
                .description("Total exact cache hits")
                .register(registry);

        Gauge.builder("polity4j.cache.misses",
                        cacheModule, ExactCacheModule::misses)
                .description("Total exact cache misses")
                .register(registry);

        Gauge.builder("polity4j.cache.hit.rate",
                        cacheModule, ExactCacheModule::hitRate)
                .description("Exact cache hit rate (0.0 to 1.0)")
                .register(registry);

        Gauge.builder("polity4j.cache.size",
                        cacheModule, m -> (double) m.size())
                .description("Current number of entries in the exact cache")
                .register(registry);
    }

    private void bindCircuitBreakerMetrics(MeterRegistry registry) {
        for (CircuitBreakerModule cb : circuitBreakers) {
            String name = cb.name();
            String provider = name.contains(":")
                    ? name.substring(name.indexOf(':') + 1)
                    : name;

            Gauge.builder("polity4j.circuit.state",
                            cb, m -> stateToDouble(m.state()))
                    .description("Circuit breaker state: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                    .tag("provider", provider)
                    .register(registry);
        }
    }

    private double stateToDouble(CircuitState state) {
        return switch (state) {
            case CLOSED    -> 0.0;
            case HALF_OPEN -> 1.0;
            case OPEN      -> 2.0;
        };
    }
}
