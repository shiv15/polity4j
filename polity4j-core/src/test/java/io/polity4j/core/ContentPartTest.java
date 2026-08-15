package io.polity4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentPartTest {

    @Test
    void testTextContentPart() {
        var part = new ContentPart.TextContentPart("Hello world");
        assertThat(part.text()).isEqualTo("Hello world");

        assertThatThrownBy(() -> new ContentPart.TextContentPart(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testImageContentPartUrl() {
        var part = ContentPart.ImageContentPart.ofUrl("https://example.com/image.png");
        assertThat(part.mediaType()).isEqualTo("image/jpeg");
        assertThat(part.data()).isEqualTo("https://example.com/image.png");
        assertThat(part.sourceType()).isEqualTo(ContentPart.SourceType.URL);
    }

    @Test
    void testImageContentPartBase64() {
        var part = ContentPart.ImageContentPart.ofBase64("image/png", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        assertThat(part.mediaType()).isEqualTo("image/png");
        assertThat(part.data()).isNotEmpty();
        assertThat(part.sourceType()).isEqualTo(ContentPart.SourceType.BASE64);
    }

    @Test
    void testDocumentContentPartBase64() {
        var part = ContentPart.DocumentContentPart.ofBase64("application/pdf", "JVBERi0xLjQK...");
        assertThat(part.mediaType()).isEqualTo("application/pdf");
        assertThat(part.data()).isEqualTo("JVBERi0xLjQK...");
        assertThat(part.sourceType()).isEqualTo(ContentPart.SourceType.BASE64);
    }
}
