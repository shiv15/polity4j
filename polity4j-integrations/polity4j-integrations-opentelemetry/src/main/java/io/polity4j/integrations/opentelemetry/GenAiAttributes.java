package io.polity4j.integrations.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * Attribute definitions adhering to official OpenTelemetry GenAI Semantic Conventions.
 */
public final class GenAiAttributes {

    private GenAiAttributes() {}

    public static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");

    public static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P = AttributeKey.doubleKey("gen_ai.request.top_p");

    public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    public static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

    public static final AttributeKey<Double> GEN_AI_USAGE_COST = AttributeKey.doubleKey("gen_ai.usage.cost");
    public static final AttributeKey<String> POLITY_CALLER_ID = AttributeKey.stringKey("polity.caller_id");
    public static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
}
