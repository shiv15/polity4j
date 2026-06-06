package io.polity4j.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimitExceptionTest {

    @Test
    public void testExceptionProperties() {
        String provider = "openai";
        long retryAfter = 5000L;
        RateLimitException exception = new RateLimitException(provider, retryAfter);

        assertEquals("Rate limit exceeded for provider 'openai', retry after 5000ms", exception.getMessage());
        assertEquals(429, exception.getStatusCode());
        assertEquals(provider, exception.provider());
        assertEquals(retryAfter, exception.retryAfterMs());
        assertNotNull(exception.getTimestamp());
    }
}
