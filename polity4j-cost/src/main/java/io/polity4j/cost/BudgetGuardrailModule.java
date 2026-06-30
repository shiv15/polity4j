package io.polity4j.cost;

import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineChain;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.BudgetExceededException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BudgetGuardrailModule implements PipelineModule {
    
    private final BudgetConfig config;
    private final BudgetEventListener listener;
    
    private final SpendTracker orgTracker = new SpendTracker();
    private final Map<String, SpendTracker> callers = new ConcurrentHashMap<>();
    
    public BudgetGuardrailModule(BudgetConfig config, BudgetEventListener listener) {
        this.config = config;
        this.listener = listener;
    }
    
    public BudgetGuardrailModule(BudgetConfig config) {
        this(config, null);
    }

    @Override
    public LlmResponse process(LlmRequest request, PipelineChain chain) {
        // Pre-flight check: Org Limit
        if (config.maxCostOrg() != null) {
            BigDecimal orgSpent = orgTracker.getSpent();
            if (orgSpent.compareTo(config.maxCostOrg()) >= 0) {
                if (listener != null) listener.onBudgetBreached(request, "org", config.maxCostOrg(), orgSpent);
                throw new BudgetExceededException(config.maxCostOrg(), orgSpent);
            }
        }
        
        // Pre-flight check: Caller Limit
        SpendTracker callerTracker = null;
        if (config.maxCostPerCaller() != null && request.callerId() != null) {
            callerTracker = callers.computeIfAbsent(request.callerId(), k -> new SpendTracker());
            BigDecimal callerSpent = callerTracker.getSpent();
            if (callerSpent.compareTo(config.maxCostPerCaller()) >= 0) {
                if (listener != null) listener.onBudgetBreached(request, "caller", config.maxCostPerCaller(), callerSpent);
                throw new BudgetExceededException(config.maxCostPerCaller(), callerSpent);
            }
        }
        
        // Flight
        LlmResponse response = chain.proceed(request);
        
        // Post-flight Reconciliation
        BigDecimal cost = response.estimatedCost();
        if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
            orgTracker.addSpend(cost);
            if (callerTracker != null) {
                callerTracker.addSpend(cost);
            }
            
            // Check Per-Call Limit Post-facto
            if (config.maxCostPerCall() != null && cost.compareTo(config.maxCostPerCall()) > 0) {
                if (listener != null) listener.onBudgetBreached(request, "call", config.maxCostPerCall(), cost);
                throw new BudgetExceededException(config.maxCostPerCall(), cost);
            }
        }
        
        return response;
    }

    @Override
    public String name() {
        return "BudgetGuardrail";
    }
}
