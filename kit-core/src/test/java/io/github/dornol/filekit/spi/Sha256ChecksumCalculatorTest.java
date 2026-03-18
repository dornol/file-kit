package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Sha256ChecksumCalculatorTest {

    private final Sha256ChecksumCalculator calculator = new Sha256ChecksumCalculator();

    @Test
    void checksum_returnsCorrectSha256Hex() {
        // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        String result = calculator.checksum("hello".getBytes());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
    }

    @Test
    void checksum_emptyBytes() {
        // SHA-256 of empty = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String result = calculator.checksum(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }

    @Test
    void checksum_returns64CharHexString() {
        String result = calculator.checksum("test".getBytes());
        assertNotNull(result);
        assertEquals(64, result.length());
        assertEquals(result, result.toLowerCase());
    }

    @Test
    void checksum_sameInputProducesSameOutput() {
        byte[] input = "consistent".getBytes();
        assertEquals(calculator.checksum(input), calculator.checksum(input));
    }

    @Test
    void checksum_differentInputProducesDifferentOutput() {
        assertNotEquals(calculator.checksum("a".getBytes()), calculator.checksum("b".getBytes()));
    }

    // ── Stream overload ─────────────────────────────────────────────

    @Test
    void checksumStream_returnsCorrectSha256Hex() {
        String result = calculator.checksum(new ByteArrayInputStream("hello".getBytes()));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
    }

    @Test
    void checksumStream_matchesByteArrayChecksum() {
        byte[] data = "some test data for streaming".getBytes();
        assertEquals(
                calculator.checksum(data),
                calculator.checksum(new ByteArrayInputStream(data)));
    }

    @Test
    void checksumStream_emptyStream_matchesEmptyArray() {
        assertEquals(
                calculator.checksum(new byte[0]),
                calculator.checksum(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void checksumStream_largeContent_spansMultipleBufferReads() {
        byte[] large = new byte[32 * 1024]; // 32 KB > 8 KB internal buffer
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i % 127);
        }
        assertEquals(
                calculator.checksum(large),
                calculator.checksum(new ByteArrayInputStream(large)));
    }

    @Test
    void checksumStream_brokenStream_throwsUncheckedIOException() {
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk failure");
            }
        };

        assertThrows(UncheckedIOException.class,
                () -> calculator.checksum(brokenStream));
    }

}
