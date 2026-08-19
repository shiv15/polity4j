package io.polity4j.core.tool;

import io.polity4j.core.ToolSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Thread-safe registry mapping tool names to execution handlers and specs.
 *
 * Prevents unguided reflective scanning vulnerabilities by requiring explicit registration
 * via `builder().register(...)` or `builder().registerBean(bean)`.
 */
public final class ToolRegistry {

    @FunctionalInterface
    public interface ToolHandler {
        Object execute(Map<String, Object> arguments) throws Exception;
    }

    private final Map<String, ToolHandler> handlers;
    private final List<ToolSpec> specs;

    private ToolRegistry(Map<String, ToolHandler> handlers, List<ToolSpec> specs) {
        this.handlers = Map.copyOf(handlers);
        this.specs = List.copyOf(specs);
    }

    public ToolHandler getHandler(String name) {
        return handlers.get(name);
    }

    public List<ToolSpec> specs() {
        return specs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, ToolHandler> handlers = new HashMap<>();
        private final List<ToolSpec> specs = new ArrayList<>();

        public Builder register(ToolSpec spec, ToolHandler handler) {
            Objects.requireNonNull(spec, "spec must not be null");
            Objects.requireNonNull(handler, "handler must not be null");
            handlers.put(spec.name(), handler);
            specs.add(spec);
            return this;
        }

        public Builder register(String name, String description, Map<String, Object> parameters, ToolHandler handler) {
            return register(ToolSpec.of(name, description, parameters), handler);
        }

        public Builder registerBean(Object bean) {
            Objects.requireNonNull(bean, "bean must not be null");
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(PolityTool.class)) {
                    PolityTool ann = method.getAnnotation(PolityTool.class);
                    String name = ann.name().isBlank() ? method.getName() : ann.name();
                    String description = ann.description();

                    Map<String, Object> properties = new HashMap<>();
                    for (var param : method.getParameters()) {
                        properties.put(param.getName(), Map.of("type", "string"));
                    }
                    Map<String, Object> parametersSchema = Map.of(
                            "type", "object",
                            "properties", properties
                    );

                    ToolSpec spec = ToolSpec.of(name, description, parametersSchema);
                    ToolHandler handler = args -> {
                        method.setAccessible(true);
                        Object[] paramValues = new Object[method.getParameterCount()];
                        var params = method.getParameters();
                        var argValues = new ArrayList<>(args.values());
                        for (int i = 0; i < params.length; i++) {
                            String paramName = params[i].getName();
                            Object val = args.get(paramName);
                            if (val == null && args.containsKey("arg" + i)) {
                                val = args.get("arg" + i);
                            }
                            if (val == null && i < argValues.size()) {
                                val = argValues.get(i);
                            }
                            paramValues[i] = val != null ? val.toString() : null;
                        }
                        return method.invoke(bean, paramValues);
                    };

                    register(spec, handler);
                }
            }
            return this;
        }

        public ToolRegistry build() {
            return new ToolRegistry(handlers, specs);
        }
    }
}
