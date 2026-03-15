package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMediaTypeDetectorTest {

    private final DefaultMediaTypeDetector detector = new DefaultMediaTypeDetector();

    @Test
    void detectFromFilename_jpeg() throws IOException {
        String result = detector.detect("photo.jpg", null);
        assertEquals("image/jpeg", result);
    }

    @Test
    void detectFromFilename_png() throws IOException {
        String result = detector.detect("image.png", null);
        assertEquals("image/png", result);
    }

    @Test
    void detectFromStream_png() throws IOException {
        // PNG magic bytes
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        InputStream stream = new ByteArrayInputStream(pngHeader);

        String result = detector.detect(null, stream);
        assertEquals("image/png", result);
    }

    @Test
    void detectFromStream_gif() throws IOException {
        // GIF89a magic bytes
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        InputStream stream = new ByteArrayInputStream(gifHeader);

        String result = detector.detect(null, stream);
        assertEquals("image/gif", result);
    }

    @Test
    void detectFallbackToOctetStream_unknownContent() throws IOException {
        byte[] unknown = {0x00, 0x01, 0x02, 0x03};
        InputStream stream = new ByteArrayInputStream(unknown);

        String result = detector.detect(null, stream);
        assertEquals("application/octet-stream", result);
    }

    @Test
    void detectFallbackToOctetStream_bothNull() throws IOException {
        String result = detector.detect(null, null);
        assertEquals("application/octet-stream", result);
    }

    @Test
    void detectStreamTakesPriorityOverFilename() throws IOException {
        // PNG magic bytes but filename says .jpg
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        InputStream stream = new ByteArrayInputStream(pngHeader);

        String result = detector.detect("photo.jpg", stream);
        // Stream detection should take priority
        assertEquals("image/png", result);
    }

    @Test
    void detectFromFilename_whenStreamDetectionFails() throws IOException {
        byte[] unknown = {0x00, 0x01, 0x02};
        InputStream stream = new ByteArrayInputStream(unknown);

        String result = detector.detect("document.pdf", stream);
        // Stream fails, falls back to filename
        assertEquals("application/pdf", result);
    }

    @Test
    void detectEmptyStream_fallsBackToFilename() throws IOException {
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        String result = detector.detect("photo.jpg", emptyStream);
        assertEquals("image/jpeg", result);
    }

}
