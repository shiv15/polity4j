package io.polity4j.integrations.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean method as guarded by the Polity4j pipeline.
 *
 * When applied to a method, any LlmRequest parameter is automatically
 * routed through the configured LlmPipeline before the method executes.
 * The method receives the LlmResponse as its return value.
 *
 * Usage:
 *
 *   @Service
 *   public class MyAiService {
 *
 *       @Polity4jGuarded
 *       public LlmResponse ask(LlmRequest request) {
 *           // pipeline executes automatically
 *           // this body is never called directly
 *           return null;
 *       }
 *   }
 *
 * The method body is replaced by the AOP advice — it does not execute.
 * The pipeline handles the call entirely.
 *
 * Requires a single LlmPipeline bean in the application context.
 * If multiple pipelines are needed, wire them manually instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Polity4jGuarded {

    /**
     * Optional name for this guarded method — used in metrics and logging.
     * Defaults to the fully qualified method name if not specified.
     */
    String name() default "";
}
