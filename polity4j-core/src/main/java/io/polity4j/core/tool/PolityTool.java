package io.polity4j.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to declare a Java method as a Polity tool function.
 *
 * Annotate bean methods with `@PolityTool` and register them cleanly using
 * `ToolRegistry.builder().registerBean(myService)`.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PolityTool {
    /**
     * The tool function name. If empty, the Java method name will be used.
     */
    String name() default "";

    /**
     * A description of what the tool function does.
     */
    String description() default "";
}
