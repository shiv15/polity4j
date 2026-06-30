package io.polity4j.cost;

import java.math.BigDecimal;

public record BudgetConfig(
    BigDecimal maxCostPerCall,
    BigDecimal maxCostPerCaller,
    BigDecimal maxCostOrg
) {
    public BudgetConfig {
        if (maxCostPerCall != null && maxCostPerCall.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("maxCostPerCall cannot be negative");
        }
        if (maxCostPerCaller != null && maxCostPerCaller.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("maxCostPerCaller cannot be negative");
        }
        if (maxCostOrg != null && maxCostOrg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("maxCostOrg cannot be negative");
        }
    }
}
