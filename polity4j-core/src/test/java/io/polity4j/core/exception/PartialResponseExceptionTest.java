package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PartialResponseExceptionTest {

    @Test
    public void testExceptionProperties() {
        String partialContent = "{\"response\": \"hello ";
        PartialResponseException exception = new PartialResponseException(partialContent);

        assertEquals("Response stream terminated before completion", exception.getMessage());
        assertEquals(502, exception.getStatusCode());
        assertEquals(partialContent, exception.partialContent());
        assertNotNull(exception.getTimestamp());
    }

    @Test
    public void testExceptionWithCause() {
        String partialContent = "partial text";
        RuntimeException cause = new RuntimeException("Connection reset");
        PartialResponseException exception = new PartialResponseException(partialContent, cause);

        assertEquals("Response stream terminated before completion", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(502, exception.getStatusCode());
    }
}
