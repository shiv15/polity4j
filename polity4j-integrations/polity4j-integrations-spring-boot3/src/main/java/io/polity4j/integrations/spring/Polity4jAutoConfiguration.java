package io.polity4j.integrations.spring;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.PipelineModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

/**
 * Spring Boot autoconfiguration for Polity4j.
 *
 * Follows standard Spring Boot autoconfiguration conventions:
 *   - All beans are conditional — back off if user defines their own
 *   - Requires a LlmClient bean to be present — we never create one
 *   - PipelineModule beans are discovered automatically from context
 *
 * What this configures automatically:
 *   1. LlmPipeline — built from all PipelineModule beans in context
 *                    in the order they are declared
 *   2. Polity4jGuardedAspect — enables @Polity4jGuarded annotation
 *
 * What the user must provide:
 *   - A LlmClient bean (their adapter)
 *   - Any PipelineModule beans they want in the pipeline
 *
 * Example user configuration:
 *
 *   @Configuration
 *   public class AiConfig {
 *
 *       @Bean
 *       public LlmClient llmClient() {
 *           return new MyAnthropicAdapter(apiKey);
 *       }
 *
 *       @Bean
 *       public RetryModule retryModule() {
 *           return new RetryModule(RetryConfig.DEFAULT);
 *       }
 *
 *       @Bean
 *       public CircuitBreakerModule circuitBreaker() {
 *           return new CircuitBreakerModule("anthropic",
 *               CircuitBreakerConfig.DEFAULT);
 *       }
 *   }
 *
 * The LlmPipeline and aspect are then created automatically.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@ConditionalOnBean(LlmClient.class)
public class Polity4jAutoConfiguration {

    /**
     * Builds the LlmPipeline from all PipelineModule beans in context.
     *
     * Module order in the pipeline matches the order Spring resolves
     * the list — use @Order or @Primary on your module beans to
     * control ordering explicitly.
     *
     * Only created if no LlmPipeline bean is already defined —
     * users who need full control can define their own.
     */
    @Bean
    @ConditionalOnMissingBean(LlmPipeline.class)
    public LlmPipeline llmPipeline(
            LlmClient client,
            @Autowired(required = false) List<PipelineModule> modules) {

        LlmPipeline.Builder builder = LlmPipeline.builder(client);

        if (modules != null) {
            modules.forEach(builder::with);
        }

        return builder.build();
    }

    /**
     * Registers the AOP aspect for @Polity4jGuarded.
     * Only created if no Polity4jGuardedAspect bean is already defined.
     */
    @Bean
    @ConditionalOnMissingBean(Polity4jGuardedAspect.class)
    public Polity4jGuardedAspect polity4jGuardedAspect(LlmPipeline pipeline) {
        return new Polity4jGuardedAspect(pipeline);
    }
}
