package io.polity4j.core;

import java.util.Objects;

/**
 * Represents a single part of a multimodal LLM message (text, image, document).
 */
public sealed interface ContentPart permits ContentPart.TextContentPart, ContentPart.ImageContentPart, ContentPart.DocumentContentPart {

    enum SourceType {
        URL,
        BASE64
    }

    record TextContentPart(String text) implements ContentPart {
        public TextContentPart {
            Objects.requireNonNull(text, "text must not be null");
        }
    }

    record ImageContentPart(String mediaType, String data, SourceType sourceType) implements ContentPart {
        public ImageContentPart {
            Objects.requireNonNull(mediaType, "mediaType must not be null");
            Objects.requireNonNull(data, "data must not be null");
            Objects.requireNonNull(sourceType, "sourceType must not be null");
        }

        public static ImageContentPart ofUrl(String url) {
            return new ImageContentPart("image/jpeg", url, SourceType.URL);
        }

        public static ImageContentPart ofBase64(String mediaType, String base64Data) {
            return new ImageContentPart(mediaType, base64Data, SourceType.BASE64);
        }
    }

    record DocumentContentPart(String mediaType, String data, SourceType sourceType) implements ContentPart {
        public DocumentContentPart {
            Objects.requireNonNull(mediaType, "mediaType must not be null");
            Objects.requireNonNull(data, "data must not be null");
            Objects.requireNonNull(sourceType, "sourceType must not be null");
        }

        public static DocumentContentPart ofBase64(String mediaType, String base64Data) {
            return new DocumentContentPart(mediaType, base64Data, SourceType.BASE64);
        }
    }
}
