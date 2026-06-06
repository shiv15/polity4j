package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OverloadedExceptionTest {

    @Test
    public void testExceptionProperties() {
        String provider = "anthropic";
        OverloadedException exception = new OverloadedException(provider);

        assertEquals("Provider 'anthropic' is overloaded — route to alternative", exception.getMessage());
        assertEquals(529, exception.getStatusCode());
        assertEquals(provider, exception.provider());
        assertNotNull(exception.getTimestamp());
    }

    @Test
    public void testExceptionWithCause() {
        String provider = "openai";
        RuntimeException cause = new RuntimeException("529 Overloaded Server");
        OverloadedException exception = new OverloadedException(provider, cause);

        assertEquals("Provider 'openai' is overloaded — route to alternative", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(529, exception.getStatusCode());
    }
}
