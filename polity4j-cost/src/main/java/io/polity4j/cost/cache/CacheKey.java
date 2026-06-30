package io.polity4j.cost.cache;

import io.polity4j.core.LlmRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic cache key derived from the content-bearing fields
 * of an LlmRequest.
 *
 * Fields included in the key:
 *   - model           — same prompt to different models yields different responses
 *   - prompt          — the user message
 *   - conversationHistory — context changes the response entirely
 *
 * Fields deliberately excluded:
 *   - maxTokens       — affects response length not content
 *   - callerId        — governance field, not content
 *   - regionContext   — governance field, not content
 *
 * The key is a SHA-256 hex digest of the above fields concatenated
 * with a null byte separator to prevent collisions between adjacent
 * field values.
 */
public final class CacheKey {

    private final String digest;

    private CacheKey(String digest) {
        this.digest = digest;
    }

    public static CacheKey from(LlmRequest request) {
        StringBuilder raw = new StringBuilder();

        raw.append(request.model());
        raw.append('\0');
        raw.append(request.prompt());
        raw.append('\0');

        // Serialize history deterministically
        // format: role:content\0 per message
        for (LlmRequest.Message message : request.conversationHistory()) {
            raw.append(message.role());
            raw.append(':');
            raw.append(message.content());
            raw.append('\0');
        }

        return new CacheKey(sha256(raw.toString()));
    }

    public String value() { return digest; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CacheKey other)) return false;
        return digest.equals(other.digest);
    }

    @Override
    public int hashCode() { return digest.hashCode(); }

    @Override
    public String toString() { return "CacheKey[" + digest + "]"; }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec — this never throws
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
