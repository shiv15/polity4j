package io.polity4j.core.exception;

import java.math.BigDecimal;

// Thrown BEFORE the API call — pre-flight, not post-facto
public final class BudgetExceededException extends PolityException {

    private final BigDecimal ceiling;
    private final BigDecimal projected;

    public BudgetExceededException(BigDecimal ceiling, BigDecimal projected) {
        super("Budget ceiling would be exceeded: projected $" + projected
              + ", ceiling is $" + ceiling, 403);
        this.ceiling = ceiling;
        this.projected = projected;
    }

    public BigDecimal ceiling() { return ceiling; }
    public BigDecimal projected() { return projected; }
}
