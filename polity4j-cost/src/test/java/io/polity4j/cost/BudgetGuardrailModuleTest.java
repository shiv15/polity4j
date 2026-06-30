package io.polity4j.cost;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.exception.BudgetExceededException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetGuardrailModuleTest {

    @Test
    void constructorThrowsNullPointerExceptionIfConfigIsNull() {
        assertThatThrownBy(() -> new BudgetGuardrailModule(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config must not be null");
    }

    @Test
    void callLevelCeilingBlocksExpensiveRequestPostFacto() {
        BudgetConfig config = new BudgetConfig(new BigDecimal("0.50"), null, null);
        BudgetGuardrailModule module = new BudgetGuardrailModule(config);
        
        LlmRequest req = LlmRequest.builder("short prompt", "gpt-4o").build();
        
        // Return cost of $1.0 > $0.50
        PipelineChain chain = request -> LlmResponse.builder("response", "gpt-4o", "openai").estimatedCost(new BigDecimal("1.00")).build();
        
        assertThatThrownBy(() -> module.process(req, chain))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("Budget ceiling would be exceeded");
    }

    @Test
    void orgLevelCeilingTracksUsage() {
        BudgetConfig config = new BudgetConfig(null, null, new BigDecimal("1.00"));
        BudgetGuardrailModule module = new BudgetGuardrailModule(config);
        
        LlmRequest req = LlmRequest.builder("short", "gpt-4o").build();
        
        PipelineChain chain = request -> LlmResponse.builder("response", "gpt-4o", "openai").estimatedCost(new BigDecimal("0.60")).build();
        
        // 1st request should pass and spend 0.60
        module.process(req, chain);
        
        // 2nd request should pass and spend another 0.60 (total 1.20)
        // Note: since it's a post-call check, the 2nd call goes through!
        module.process(req, chain);
        
        // 3rd request should fail pre-flight because spent 1.20 > limit 1.00
        assertThatThrownBy(() -> module.process(req, chain))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("Budget ceiling would be exceeded");
    }

    @Test
    void callerLevelCeilingTracksUsage() {
        BudgetConfig config = new BudgetConfig(null, new BigDecimal("1.00"), null);
        BudgetGuardrailModule module = new BudgetGuardrailModule(config);
        
        LlmRequest reqCaller1 = LlmRequest.builder("short", "gpt-4o").callerId("alice").build();
        LlmRequest reqCaller2 = LlmRequest.builder("short", "gpt-4o").callerId("bob").build();
        
        PipelineChain chain = request -> LlmResponse.builder("response", "gpt-4o", "openai").estimatedCost(new BigDecimal("1.00")).build();
        
        // Caller 1 spends 1.00
        module.process(reqCaller1, chain);
        
        // Caller 1 is blocked on next request
        assertThatThrownBy(() -> module.process(reqCaller1, chain))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("Budget ceiling would be exceeded");
                
        // Caller 2 can still make a request
        module.process(reqCaller2, chain);
    }
    
    @Test
    void exceptionDoesNotAddSpend() {
        BudgetConfig config = new BudgetConfig(null, null, new BigDecimal("1.00"));
        BudgetGuardrailModule module = new BudgetGuardrailModule(config);
        
        LlmRequest req = LlmRequest.builder("short", "gpt-4o").build();
        
        PipelineChain failingChain = request -> {
            throw new RuntimeException("API failure");
        };
        
        assertThatThrownBy(() -> module.process(req, failingChain))
                .isInstanceOf(RuntimeException.class);
                
        PipelineChain successChain = request -> LlmResponse.builder("response", "gpt-4o", "openai").estimatedCost(new BigDecimal("1.00")).build();
        module.process(req, successChain); // Passes since previous failed and added 0 spend
        
        assertThatThrownBy(() -> module.process(req, successChain))
                .isInstanceOf(BudgetExceededException.class); // Fails since spend is now 1.00
    }
}
