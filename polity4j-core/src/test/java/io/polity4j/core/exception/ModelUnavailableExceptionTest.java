package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelUnavailableExceptionTest {

    @Test
    public void testExceptionProperties() {
        String model = "claude-3-5-sonnet";
        String provider = "anthropic";
        ModelUnavailableException exception = new ModelUnavailableException(model, provider);

        assertEquals("Model 'claude-3-5-sonnet' unavailable at provider 'anthropic'", exception.getMessage());
        assertEquals(503, exception.getStatusCode());
        assertEquals(model, exception.model());
        assertEquals(provider, exception.provider());
        assertNotNull(exception.getTimestamp());
    }

    @Test
    public void testExceptionWithCause() {
        String model = "gpt-4";
        String provider = "openai";
        RuntimeException cause = new RuntimeException("503 Service Unavailable");
        ModelUnavailableException exception = new ModelUnavailableException(model, provider, cause);

        assertEquals("Model 'gpt-4' unavailable at provider 'openai'", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(503, exception.getStatusCode());
    }
}
