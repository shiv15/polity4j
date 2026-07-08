package io.polity4j.quality.response;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;
import io.polity4j.core.exception.ResponseValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A PipelineModule that validates structural correctness of LLM responses
 * using user-supplied validators and executes a corrective retry loop
 * on validation failure.
 */
public final class ResponseValidatorModule implements PipelineModule {

    private final ResponseValidator validator;
    private final ResponseValidatorConfig config;

    public ResponseValidatorModule(ResponseValidator validator) {
        this(validator, ResponseValidatorConfig.DEFAULT);
    }

    public ResponseValidatorModule(ResponseValidator validator, ResponseValidatorConfig config) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain next) throws PolityException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(next, "next must not be null");

        LlmRequest currentRequest = request;
        int maxRetries = config.maxRetries();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            LlmResponse response = next.proceed(currentRequest);
            ValidationResult result = validator.validate(response);

            if (result.isValid()) {
                return response;
            }

            if (attempt == maxRetries) {
                String reason = result.failureReason().orElse("Response failed validation check");
                throw new ResponseValidationException(
                        "Response failed validation after " + maxRetries + " retries: " + reason,
                        response.content()
                );
            }

            // We have retries left. Build corrective prompt and update history.
            String correctivePrompt = result.failureReason().orElse(config.fallbackCorrectivePrompt());

            List<LlmRequest.Message> newHistory = new ArrayList<>(currentRequest.conversationHistory());
            newHistory.add(new LlmRequest.Message("assistant", response.content()));
            newHistory.add(new LlmRequest.Message("user", correctivePrompt));

            currentRequest = LlmRequest.builder(currentRequest.prompt(), currentRequest.model())
                    .maxTokens(currentRequest.maxTokens())
                    .callerId(currentRequest.callerId())
                    .regionContext(currentRequest.regionContext())
                    .conversationHistory(newHistory)
                    .build();
        }

        throw new ResponseValidationException("Unexpected state: retries exhausted without exception", "");
    }

    @Override
    public String name() {
        return "response-validator";
    }
}
