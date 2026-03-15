package io.github.dornol.filekit.spring.validator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TikaMediaTypeDetectorTest {

    private final TikaMediaTypeDetector detector = new TikaMediaTypeDetector();

    @Test
    void detectPng_fromMagicBytes() throws IOException {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        InputStream stream = new ByteArrayInputStream(pngHeader);

        String result = detector.detect("image.png", stream);
        assertEquals("image/png", result);
    }

    @Test
    void detectJpeg_fromMagicBytes() throws IOException {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        InputStream stream = new ByteArrayInputStream(jpegHeader);

        String result = detector.detect("photo.jpg", stream);
        assertEquals("image/jpeg", result);
    }

    @Test
    void detectGif_fromMagicBytes() throws IOException {
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        InputStream stream = new ByteArrayInputStream(gifHeader);

        String result = detector.detect("image.gif", stream);
        assertEquals("image/gif", result);
    }

    @Test
    void detectPdf_fromMagicBytes() throws IOException {
        byte[] pdfHeader = "%PDF-1.4".getBytes();
        InputStream stream = new ByteArrayInputStream(pdfHeader);

        String result = detector.detect("document.pdf", stream);
        assertEquals("application/pdf", result);
    }

    @Test
    void detectFromFilename_whenStreamIsNull() throws IOException {
        String result = detector.detect("photo.jpg", null);
        assertEquals("image/jpeg", result);
    }

    @Test
    void detectOctetStream_whenUnknown() throws IOException {
        byte[] unknown = {0x00, 0x01, 0x02, 0x03};
        InputStream stream = new ByteArrayInputStream(unknown);

        String result = detector.detect(null, stream);
        assertEquals("application/octet-stream", result);
    }

    @Test
    void detectFromContent_overridesFilenameExtension() throws IOException {
        // PNG content but .jpg extension - Tika should detect actual content
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        InputStream stream = new ByteArrayInputStream(pngHeader);

        String result = detector.detect("fake.jpg", stream);
        assertEquals("image/png", result);
    }

}
