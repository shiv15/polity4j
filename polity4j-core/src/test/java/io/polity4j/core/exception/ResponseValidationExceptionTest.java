package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ResponseValidationExceptionTest {

    @Test
    public void testExceptionProperties() {
        String invalidContent = "{\"value\": -5}";
        String message = "Validation failed: value must be positive";
        ResponseValidationException exception = new ResponseValidationException(message, invalidContent);

        assertEquals(message, exception.getMessage());
        assertEquals(422, exception.getStatusCode());
        assertEquals(invalidContent, exception.invalidResponseContent());
        assertNotNull(exception.getTimestamp());
    }

    @Test
    public void testExceptionWithCause() {
        String invalidContent = "not-json";
        String message = "Failed to parse JSON";
        RuntimeException cause = new RuntimeException("Malformed input");
        ResponseValidationException exception = new ResponseValidationException(message, invalidContent, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(422, exception.getStatusCode());
        assertEquals(invalidContent, exception.invalidResponseContent());
    }
}
