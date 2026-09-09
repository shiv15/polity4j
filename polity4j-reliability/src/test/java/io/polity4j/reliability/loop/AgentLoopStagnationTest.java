package io.polity4j.reliability.loop;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.AgentLoopException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopStagnationTest {

    private static final String MODEL = "claude-3-5-sonnet-20241022";

    private static LlmRequest request(String prompt) {
        return LlmRequest.builder(prompt, MODEL)
                .callerId("agent-session-1")
                .build();
    }

    private static PipelineChain returnsResult(String result) {
        return req -> LlmResponse
                .builder(result, MODEL, "anthropic")
                .estimatedCost(BigDecimal.ZERO)
                .build();
    }

    private static AgentLoopDetectorModule moduleWithLimit(int limit) {
        return new AgentLoopDetectorModule(
                AgentLoopConfig.builder()
                        .stagnationLimit(limit)
                        .resultAwareStagnation(true)
                        .build());
    }

    // ------------------------------------------------------------------
    // nonloop_04_legitimate_polling
    // Identical prompt, changing result — must NOT trip
    // ------------------------------------------------------------------

    @Test
    void legitimatePolling_identicalPromptChangingResult_doesNotTrip() {
        var module = moduleWithLimit(4);
        String prompt = "check status of job_X";

        // 6-call polling sequence — result changes each time
        module.process(request(prompt), returnsResult("pending"));
        module.process(request(prompt), returnsResult("pending"));
        module.process(request(prompt), returnsResult("running"));
        module.process(request(prompt), returnsResult("running"));
        module.process(request(prompt), returnsResult("running"));
        module.process(request(prompt), returnsResult("complete"));

        // No exception thrown — all 6 calls completed successfully
    }

    @Test
    void legitimatePolling_resultChangesOnFourthCall_doesNotTrip() {
        var module = moduleWithLimit(4);
        String prompt = "check status of job_X";

        // This is exactly what failed in nonloop_04 benchmark
        // Call 4 previously tripped — should not with result-aware check
        module.process(request(prompt), returnsResult("pending"));
        module.process(request(prompt), returnsResult("pending"));
        module.process(request(prompt), returnsResult("pending"));

        // Fourth call — result changes, should reset stagnation count
        var response = module.process(
                request(prompt), returnsResult("running"));

        assertThat(response.content()).isEqualTo("running");
    }

    // ------------------------------------------------------------------
    // loop_01_exact_repetition
    // Identical prompt AND result — must trip
    // ------------------------------------------------------------------

    @Test
    void exactRepetition_identicalPromptAndResult_trips() {
        var module = moduleWithLimit(3);

        assertThatThrownBy(() -> {
            module.process(request("same"), returnsResult("same result"));
            module.process(request("same"), returnsResult("same result"));
            module.process(request("same"), returnsResult("same result"));
        })
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.STAGNATION_DETECTED);
    }

    // ------------------------------------------------------------------
    // Adversarial — identical prompt/result except final call
    // Must not trip prematurely
    // ------------------------------------------------------------------

    @Test
    void adversarial_identicalUntilLastCall_doesNotTripEarly() {
        var module = moduleWithLimit(3);
        String prompt = "process item";

        // Two identical interactions — not yet at limit
        module.process(request(prompt), returnsResult("error: retry"));
        module.process(request(prompt), returnsResult("error: retry"));

        // Third call — result changes, resets count
        var response = module.process(
                request(prompt), returnsResult("success"));

        assertThat(response.content()).isEqualTo("success");
        assertThat(module.session("agent-session-1").consecutiveStagnantCalls())
                .isEqualTo(1); // reset to 1 on the changed result
    }

    // ------------------------------------------------------------------
    // Legacy behavior — prompt-only mode
    // ------------------------------------------------------------------

    @Test
    void legacyMode_promptOnly_tripsOnIdenticalPromptRegardlessOfResult() {
        var module = new AgentLoopDetectorModule(
                AgentLoopConfig.builder()
                        .stagnationLimit(3)
                        .resultAwareStagnation(false) // legacy
                        .build());

        String prompt = "check status of job_X";

        assertThatThrownBy(() -> {
            module.process(request(prompt), returnsResult("pending"));
            module.process(request(prompt), returnsResult("running"));
            module.process(request(prompt), returnsResult("complete"));
        })
                .isInstanceOf(AgentLoopException.class)
                .extracting(e -> ((AgentLoopException) e).tripReason())
                .isEqualTo(AgentLoopException.TripReason.STAGNATION_DETECTED);
    }

    // ------------------------------------------------------------------
    // Default config uses result-aware checking
    // ------------------------------------------------------------------

    @Test
    void defaultConfig_isResultAware() {
        assertThat(AgentLoopConfig.DEFAULT.resultAwareStagnation())
                .isTrue();
    }
}
