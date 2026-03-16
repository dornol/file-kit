package io.github.dornol.filekit.scan;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirusScannerTest {

    @Test
    void scanInputStream_defaultDelegatesToByteArray() {
        VirusScanner scanner = fileBytes -> {
            assertEquals("hello", new String(fileBytes));
            return ScanResult.clean();
        };

        ScanResult result = scanner.scan(new ByteArrayInputStream("hello".getBytes()));
        assertEquals(ScanResult.Status.CLEAN, result.status());
    }

    @Test
    void scanInputStream_propagatesInfectedResult() {
        VirusScanner scanner = fileBytes -> ScanResult.infected("EICAR");

        ScanResult result = scanner.scan(new ByteArrayInputStream("data".getBytes()));
        assertEquals(ScanResult.Status.INFECTED, result.status());
        assertEquals("EICAR", result.message());
    }

    @Test
    void scanInputStream_propagatesErrorResult() {
        VirusScanner scanner = fileBytes -> ScanResult.error("timeout");

        ScanResult result = scanner.scan(new ByteArrayInputStream("data".getBytes()));
        assertEquals(ScanResult.Status.ERROR, result.status());
        assertEquals("timeout", result.message());
    }

    @Test
    void scanInputStream_wrapsIOException() {
        VirusScanner scanner = fileBytes -> ScanResult.clean();

        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stream broken");
            }
        };

        assertThrows(UncheckedIOException.class, () -> scanner.scan(failingStream));
    }

    @Test
    void scanInputStream_emptyStream() {
        VirusScanner scanner = fileBytes -> {
            assertEquals(0, fileBytes.length);
            return ScanResult.clean();
        };

        ScanResult result = scanner.scan(new ByteArrayInputStream(new byte[0]));
        assertEquals(ScanResult.Status.CLEAN, result.status());
    }
}
