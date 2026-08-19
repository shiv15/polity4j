package io.polity4j.core.tool;

import io.polity4j.core.ContentPart;
import io.polity4j.core.FinishReason;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.ToolCall;
import io.polity4j.core.exception.PolityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Automated Tool Execution Module.
 *
 * Intercepts LLM responses with `FinishReason.TOOL_CALLS`, resolves the matching tool handler
 * in `ToolRegistry`, executes the tool, appends the tool result to the conversation history,
 * and recursively re-invokes the pipeline chain up to a configured `maxDepth`.
 */
public final class ToolExecutionModule implements PipelineModule {

    public static final int DEFAULT_MAX_DEPTH = 5;

    private final ToolRegistry registry;
    private final int maxDepth;

    public ToolExecutionModule(ToolRegistry registry) {
        this(registry, DEFAULT_MAX_DEPTH);
    }

    public ToolExecutionModule(ToolRegistry registry, int maxDepth) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be greater than 0");
        }
        this.maxDepth = maxDepth;
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        return processRecursive(request, next, 0);
    }

    private LlmResponse processRecursive(LlmRequest request, PipelineChain next, int currentDepth) throws PolityException {
        // Automatically attach tool specs if request has no tools attached
        LlmRequest activeRequest = request;
        if (activeRequest.tools().isEmpty() && !registry.specs().isEmpty()) {
            activeRequest = LlmRequest.builder(request.prompt(), request.model())
                    .maxTokens(request.maxTokens())
                    .callerId(request.callerId())
                    .regionContext(request.regionContext())
                    .conversationHistory(request.conversationHistory())
                    .temperature(request.temperature())
                    .topP(request.topP())
                    .frequencyPenalty(request.frequencyPenalty())
                    .presencePenalty(request.presencePenalty())
                    .additionalParams(request.additionalParams())
                    .systemPrompt(request.systemPrompt())
                    .tools(registry.specs())
                    .build();
        }

        LlmResponse response = next.proceed(activeRequest);

        if (response.finishReason() == FinishReason.TOOL_CALLS && !response.toolCalls().isEmpty() && currentDepth < maxDepth) {
            List<LlmRequest.Message> updatedHistory = new ArrayList<>(activeRequest.conversationHistory());

            for (ToolCall toolCall : response.toolCalls()) {
                ToolRegistry.ToolHandler handler = registry.getHandler(toolCall.name());
                String resultText;
                if (handler != null) {
                    try {
                        Object result = handler.execute(toolCall.arguments());
                        resultText = result != null ? result.toString() : "success";
                    } catch (Exception e) {
                        resultText = "Error executing tool " + toolCall.name() + ": " + e.getMessage();
                    }
                } else {
                    resultText = "Unknown tool: " + toolCall.name();
                }

                // Add tool call assistant turn and tool response turn
                updatedHistory.add(new LlmRequest.Message("assistant", "Tool call: " + toolCall.name() + "(" + toolCall.arguments() + ")"));
                updatedHistory.add(new LlmRequest.Message("tool", "Tool result [" + toolCall.name() + "]: " + resultText));
            }

            LlmRequest nextRequest = LlmRequest.builder(activeRequest.prompt(), activeRequest.model())
                    .maxTokens(activeRequest.maxTokens())
                    .callerId(activeRequest.callerId())
                    .regionContext(activeRequest.regionContext())
                    .conversationHistory(updatedHistory)
                    .temperature(activeRequest.temperature())
                    .topP(activeRequest.topP())
                    .frequencyPenalty(activeRequest.frequencyPenalty())
                    .presencePenalty(activeRequest.presencePenalty())
                    .additionalParams(activeRequest.additionalParams())
                    .systemPrompt(activeRequest.systemPrompt())
                    .tools(activeRequest.tools())
                    .build();

            return processRecursive(nextRequest, next, currentDepth + 1);
        }

        return response;
    }

    @Override
    public String name() {
        return "tool-execution-module";
    }
}
