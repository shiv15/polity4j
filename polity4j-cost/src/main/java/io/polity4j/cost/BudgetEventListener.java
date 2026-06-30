package io.polity4j.cost;

import io.polity4j.core.LlmRequest;

import java.math.BigDecimal;

public interface BudgetEventListener {
    void onBudgetBreached(LlmRequest request, String scope, BigDecimal limit, BigDecimal spent);
}
