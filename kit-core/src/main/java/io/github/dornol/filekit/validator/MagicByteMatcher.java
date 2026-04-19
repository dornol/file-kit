package io.github.dornol.filekit.validator;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Matches the leading bytes of a file against a small table of common-format
 * magic-byte signatures. Used by {@link DefaultMediaTypeDetector} as the
 * first detection pass before the JDK {@code URLConnection} probes.
 *
 * <p>The signature table is intentionally limited to widely-used formats
 * that the JDK detector misses — notably PDF, ZIP (and its OOXML variants),
 * MP4, and WebP. Broader coverage is the job of Apache Tika; callers who
 * need it can plug in their own {@link MediaTypeDetector}.</p>
 *
 * @since 0.1.22
 */
final class MagicByteMatcher {

    /** Maximum bytes needed by the largest signature in the table (WebP at offset 12). */
    static final int MAX_HEADER_BYTES = 16;

    private record Signature(int offset, byte[] bytes, String mimeType) {
        boolean matches(byte[] header, int headerLen) {
            return offset + bytes.length <= headerLen
                    && Arrays.equals(header, offset, offset + bytes.length, bytes, 0, bytes.length);
        }
    }

    private static final List<Signature> SIGNATURES = List.of(
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            new Signature(0, new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "image/png"),
            // JPEG: FF D8 FF
            new Signature(0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg"),
            // GIF: 47 49 46 38
            new Signature(0, "GIF8".getBytes(StandardCharsets.US_ASCII), "image/gif"),
            // PDF: 25 50 44 46
            new Signature(0, "%PDF".getBytes(StandardCharsets.US_ASCII), "application/pdf"),
            // ZIP (covers JAR, DOCX, XLSX, PPTX, APK before deeper inspection)
            new Signature(0, new byte[]{'P', 'K', 0x03, 0x04}, "application/zip"),
            // BMP: 42 4D
            new Signature(0, new byte[]{'B', 'M'}, "image/bmp"),
            // MP4/MOV: offset 4-7 = "ftyp"
            new Signature(4, "ftyp".getBytes(StandardCharsets.US_ASCII), "video/mp4"),
            // OGG: "OggS"
            new Signature(0, "OggS".getBytes(StandardCharsets.US_ASCII), "audio/ogg"),
            // Zstandard: 28 B5 2F FD
            new Signature(0, new byte[]{0x28, (byte) 0xB5, 0x2F, (byte) 0xFD}, "application/zstd")
    );

    private MagicByteMatcher() {}

    /**
     * Attempts to identify the media type from the first {@code headerLen}
     * bytes of a file.
     *
     * @param header    buffer containing leading bytes; only indices 0..headerLen-1 are inspected
     * @param headerLen number of valid bytes in {@code header}
     * @return the detected MIME type, or {@code null} if no signature matches
     */
    static @Nullable String match(byte[] header, int headerLen) {
        // WebP is a RIFF container — needs a two-part check (RIFF at 0, WEBP at 8).
        if (headerLen >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        for (Signature sig : SIGNATURES) {
            if (sig.matches(header, headerLen)) {
                return sig.mimeType();
            }
        }
        return null;
    }
}
