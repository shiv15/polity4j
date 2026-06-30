package io.polity4j.cost;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

public class SpendTracker {
    private final AtomicReference<BigDecimal> spent = new AtomicReference<>(BigDecimal.ZERO);
    
    public BigDecimal getSpent() {
        return spent.get();
    }
    
    public void addSpend(BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        spent.accumulateAndGet(cost, BigDecimal::add);
    }
}
