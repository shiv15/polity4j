package io.polity4j.quality.response;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.ResponseValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseValidatorModuleTest {

    private static final String MODEL = "test-model";

    private static LlmResponse response(String content) {
        return LlmResponse.builder(content, MODEL, "test-provider")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    private static class MockChain implements PipelineChain {
        private final List<LlmResponse> responses;
        private final List<LlmRequest> requestsReceived = new ArrayList<>();
        private int index = 0;

        MockChain(LlmResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public LlmResponse proceed(LlmRequest request) {
            requestsReceived.add(request);
            if (index < responses.size()) {
                return responses.get(index++);
            }
            throw new IllegalStateException("No more mock responses configured");
        }
    }

    @Test
    void validationSucceedsOnFirstAttempt() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse response) {
                return response.content().equals("valid")
                        ? ValidationResult.success()
                        : ValidationResult.failure("Invalid response");
            }
        };

        var module = new ResponseValidatorModule(validator);
        var chain = new MockChain(response("valid"));
        var request = LlmRequest.builder("prompt", MODEL).build();

        LlmResponse result = module.process(request, chain);

        assertThat(result.content()).isEqualTo("valid");
        assertThat(chain.requestsReceived).hasSize(1);
        assertThat(chain.requestsReceived.get(0).conversationHistory()).isEmpty();
    }

    @Test
    void validationFailsThenSucceedsOnRetry() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse response) {
                return response.content().equals("valid")
                        ? ValidationResult.success()
                        : ValidationResult.failure("Must say valid");
            }
        };

        var module = new ResponseValidatorModule(validator);
        var chain = new MockChain(response("invalid"), response("valid"));
        var request = LlmRequest.builder("prompt", MODEL).build();

        LlmResponse result = module.process(request, chain);

        assertThat(result.content()).isEqualTo("valid");
        assertThat(chain.requestsReceived).hasSize(2);

        // First request is original
        assertThat(chain.requestsReceived.get(0).conversationHistory()).isEmpty();

        // Second request has corrective feedback injected into history
        List<LlmRequest.Message> history = chain.requestsReceived.get(1).conversationHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo("assistant");
        assertThat(history.get(0).content()).isEqualTo("invalid");
        assertThat(history.get(1).role()).isEqualTo("user");
        assertThat(history.get(1).content()).isEqualTo("Must say valid");
    }

    @Test
    void validationFailsExhaustingRetries() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse response) {
                return ValidationResult.failure("Always invalid");
            }
        };

        // default is 3 retries (4 total attempts)
        var module = new ResponseValidatorModule(validator);
        var chain = new MockChain(
                response("attempt 1"),
                response("attempt 2"),
                response("attempt 3"),
                response("attempt 4")
        );
        var request = LlmRequest.builder("prompt", MODEL).build();

        assertThatThrownBy(() -> module.process(request, chain))
                .isInstanceOf(ResponseValidationException.class)
                .hasMessageContaining("Response failed validation after 3 retries")
                .hasMessageContaining("Always invalid")
                .satisfies(ex -> {
                    ResponseValidationException rve = (ResponseValidationException) ex;
                    assertThat(rve.invalidResponseContent()).isEqualTo("attempt 4");
                });

        assertThat(chain.requestsReceived).hasSize(4);
    }

    @Test
    void respectsCustomConfigOptions() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse response) {
                // Return failure with empty/null reason to force fallback
                return ValidationResult.failure(null);
            }
        };

        var config = ResponseValidatorConfig.builder()
                .maxRetries(1)
                .fallbackCorrectivePrompt("Custom fallback prompt")
                .build();

        var module = new ResponseValidatorModule(validator, config);
        var chain = new MockChain(response("attempt 1"), response("attempt 2"));
        var request = LlmRequest.builder("prompt", MODEL).build();

        assertThatThrownBy(() -> module.process(request, chain))
                .isInstanceOf(ResponseValidationException.class)
                .hasMessageContaining("Response failed validation after 1 retries")
                .satisfies(ex -> {
                    ResponseValidationException rve = (ResponseValidationException) ex;
                    assertThat(rve.invalidResponseContent()).isEqualTo("attempt 2");
                });

        assertThat(chain.requestsReceived).hasSize(2);
        List<LlmRequest.Message> history = chain.requestsReceived.get(1).conversationHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(1).content()).isEqualTo("Custom fallback prompt");
    }

    @Test
    void constructorNullValidation() {
        assertThatThrownBy(() -> new ResponseValidatorModule(null))
                .isInstanceOf(NullPointerException.class);

        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse r) {
                return ValidationResult.success();
            }
        };

        assertThatThrownBy(() -> new ResponseValidatorModule(validator, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void processNullValidation() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse r) {
                return ValidationResult.success();
            }
        };
        var module = new ResponseValidatorModule(validator);

        assertThatThrownBy(() -> module.process(null, new MockChain()))
                .isInstanceOf(NullPointerException.class);

        var request = LlmRequest.builder("prompt", MODEL).build();
        assertThatThrownBy(() -> module.process(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameIsCorrect() {
        var validator = new ResponseValidator() {
            @Override
            public ValidationResult validate(LlmResponse r) {
                return ValidationResult.success();
            }
        };
        var module = new ResponseValidatorModule(validator);
        assertThat(module.name()).isEqualTo("response-validator");
    }
}
