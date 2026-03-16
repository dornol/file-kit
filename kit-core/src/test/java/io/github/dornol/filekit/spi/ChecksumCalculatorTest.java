package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecksumCalculatorTest {

    @Test
    void checksumInputStream_defaultDelegatesToByteArray() {
        ChecksumCalculator calculator = bytes -> "hash-of-" + new String(bytes);

        String result = calculator.checksum(new ByteArrayInputStream("hello".getBytes()));
        assertEquals("hash-of-hello", result);
    }

    @Test
    void checksumInputStream_emptyStream() {
        ChecksumCalculator calculator = bytes -> {
            assertEquals(0, bytes.length);
            return "empty-hash";
        };

        String result = calculator.checksum(new ByteArrayInputStream(new byte[0]));
        assertEquals("empty-hash", result);
    }

    @Test
    void checksumInputStream_wrapsIOException() {
        ChecksumCalculator calculator = bytes -> "should-not-reach";

        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stream broken");
            }
        };

        assertThrows(UncheckedIOException.class, () -> calculator.checksum(failingStream));
    }
}
