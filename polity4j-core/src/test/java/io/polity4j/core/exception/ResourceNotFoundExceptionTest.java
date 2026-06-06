package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

public class ResourceNotFoundExceptionTest {

    @Test
    public void testExceptionProperties() {
        String message = "User not found";
        ResourceNotFoundException exception = new ResourceNotFoundException(message);

        assertEquals(message, exception.getMessage());
        assertEquals(404, exception.getStatusCode());
        assertNotNull(exception.getTimestamp());
        assertTrue(exception.getTimestamp().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    public void testExceptionWithCause() {
        String message = "Article not found";
        RuntimeException cause = new RuntimeException("DB Connection failed");
        ResourceNotFoundException exception = new ResourceNotFoundException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(404, exception.getStatusCode());
    }
}
