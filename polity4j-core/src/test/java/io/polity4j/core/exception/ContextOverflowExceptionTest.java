package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ContextOverflowExceptionTest {

    @Test
    public void testExceptionProperties() {
        int tokenCount = 10000;
        int contextLimit = 8192;
        ContextOverflowException exception = new ContextOverflowException(tokenCount, contextLimit);

        assertEquals("Prompt exceeds context window: 10000 tokens, limit is 8192", exception.getMessage());
        assertEquals(400, exception.getStatusCode());
        assertEquals(tokenCount, exception.tokenCount());
        assertEquals(contextLimit, exception.contextLimit());
        assertNotNull(exception.getTimestamp());
    }
}
