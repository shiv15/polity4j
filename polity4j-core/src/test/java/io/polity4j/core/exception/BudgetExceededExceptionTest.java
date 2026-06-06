package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class BudgetExceededExceptionTest {

    @Test
    public void testExceptionProperties() {
        BigDecimal ceiling = new BigDecimal("10.00");
        BigDecimal projected = new BigDecimal("12.50");
        BudgetExceededException exception = new BudgetExceededException(ceiling, projected);

        assertEquals("Budget ceiling would be exceeded: projected $12.50, ceiling is $10.00", exception.getMessage());
        assertEquals(403, exception.getStatusCode());
        assertEquals(ceiling, exception.ceiling());
        assertEquals(projected, exception.projected());
        assertNotNull(exception.getTimestamp());
    }
}
