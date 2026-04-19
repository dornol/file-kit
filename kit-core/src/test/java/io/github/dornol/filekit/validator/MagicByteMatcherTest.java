package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MagicByteMatcherTest {

    // M1
    @Test
    void png_detectedAsImagePng() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals("image/png", MagicByteMatcher.match(header, header.length));
    }

    // M2
    @Test
    void jpeg_detectedAsImageJpeg() {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertEquals("image/jpeg", MagicByteMatcher.match(header, header.length));
    }

    // M3
    @Test
    void gif_detectedAsImageGif() {
        byte[] header = {'G', 'I', 'F', '8', '9', 'a'};
        assertEquals("image/gif", MagicByteMatcher.match(header, header.length));
    }

    // M4
    @Test
    void pdf_detectedAsApplicationPdf() {
        byte[] header = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
        assertEquals("application/pdf", MagicByteMatcher.match(header, header.length));
    }

    // M5
    @Test
    void zip_detectedAsApplicationZip() {
        byte[] header = {'P', 'K', 0x03, 0x04, 0x14, 0x00};
        assertEquals("application/zip", MagicByteMatcher.match(header, header.length));
    }

    // M6
    @Test
    void bmp_detectedAsImageBmp() {
        byte[] header = {'B', 'M', 0x36, 0x00};
        assertEquals("image/bmp", MagicByteMatcher.match(header, header.length));
    }

    // M7
    @Test
    void webp_detectedAsImageWebp() {
        // RIFF....WEBP where ... is size (ignored in matcher)
        byte[] header = new byte[]{
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P',
                0, 0, 0, 0
        };
        assertEquals("image/webp", MagicByteMatcher.match(header, header.length));
    }

    // M8
    @Test
    void mp4_detectedAsVideoMp4() {
        // 4 bytes box size + "ftyp"
        byte[] header = new byte[]{
                0x00, 0x00, 0x00, 0x20,
                'f', 't', 'y', 'p',
                'm', 'p', '4', '2'
        };
        assertEquals("video/mp4", MagicByteMatcher.match(header, header.length));
    }

    // M9
    @Test
    void ogg_detectedAsAudioOgg() {
        byte[] header = {'O', 'g', 'g', 'S', 0, 2};
        assertEquals("audio/ogg", MagicByteMatcher.match(header, header.length));
    }

    // M10
    @Test
    void randomBytes_returnsNull() {
        byte[] header = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        assertNull(MagicByteMatcher.match(header, header.length));
    }

    // M11
    @Test
    void emptyHeader_returnsNull() {
        assertNull(MagicByteMatcher.match(new byte[0], 0));
        assertNull(MagicByteMatcher.match(new byte[16], 0));
    }

    // M12
    @Test
    void tooShortForSignature_returnsNull() {
        // "PK" alone — less than the 4-byte ZIP signature
        byte[] header = {'P', 'K'};
        assertNull(MagicByteMatcher.match(header, header.length));
    }

    // M13 — Zstandard
    @Test
    void zstd_detected() {
        byte[] header = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};
        assertEquals("application/zstd", MagicByteMatcher.match(header, header.length));
    }

    // M14 — RIFF without WEBP → not matched (possibly audio/wav or avi, we don't cover)
    @Test
    void riffWithoutWebpSignature_returnsNull() {
        byte[] header = new byte[]{
                'R', 'I', 'F', 'F',
                0, 0, 0, 0,
                'W', 'A', 'V', 'E',
                0, 0, 0, 0
        };
        assertNull(MagicByteMatcher.match(header, header.length));
    }

    // M15 — headerLen shorter than buffer array length should be honored
    @Test
    void headerLenLimitsInspection() {
        byte[] fullBuffer = new byte[16];
        fullBuffer[0] = (byte) 0x89;
        fullBuffer[1] = 'P';
        fullBuffer[2] = 'N';
        fullBuffer[3] = 'G';
        // Pretend we only read 2 bytes — should NOT match PNG (needs 4)
        assertNull(MagicByteMatcher.match(fullBuffer, 2));
    }
}
