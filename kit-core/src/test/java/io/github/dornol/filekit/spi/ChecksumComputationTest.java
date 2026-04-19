package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecksumComputationTest {

    // N1 — Sha256 streaming matches the byte[] API result
    @Test
    void sha256_streamingMatchesByteApi() {
        Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
        byte[] data = "hello world".getBytes();

        String viaBytes = calc.checksum(data);

        ChecksumComputation comp = calc.newComputation();
        comp.update(data, 0, 5);
        comp.update(data, 5, data.length - 5);
        String viaStreaming = comp.finish();

        assertEquals(viaBytes, viaStreaming);
    }

    // N2 — custom calculator without override uses BufferingComputation fallback
    @Test
    void customCalculator_usesBufferingFallback() {
        ChecksumCalculator stub = bytes -> "stub:" + bytes.length;
        ChecksumComputation comp = stub.newComputation();
        byte[] data = {1, 2, 3, 4};
        comp.update(data, 0, 2);
        comp.update(data, 2, 2);
        assertEquals("stub:4", comp.finish());
    }

    // N3 — double finish throws (buffering)
    @Test
    void buffering_doubleFinish_throws() {
        ChecksumCalculator stub = bytes -> "x";
        ChecksumComputation comp = stub.newComputation();
        comp.finish();
        assertThrows(IllegalStateException.class, comp::finish);
        assertThrows(IllegalStateException.class, () -> comp.update(new byte[1], 0, 1));
    }

    // N3b — double finish throws (MessageDigest path)
    @Test
    void sha256_doubleFinish_throws() {
        ChecksumComputation comp = new Sha256ChecksumCalculator().newComputation();
        comp.finish();
        assertThrows(IllegalStateException.class, comp::finish);
        assertThrows(IllegalStateException.class, () -> comp.update(new byte[1], 0, 1));
    }

    // N4 — newComputation returns fresh, independent instances
    @Test
    void newComputation_returnsIndependentInstances() {
        Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
        ChecksumComputation a = calc.newComputation();
        ChecksumComputation b = calc.newComputation();
        assertNotSame(a, b);

        a.update("hello".getBytes(), 0, 5);
        b.update("world".getBytes(), 0, 5);

        assertEquals(calc.checksum("hello".getBytes()), a.finish());
        assertEquals(calc.checksum("world".getBytes()), b.finish());
    }

    // Large streaming input — doesn't hold onto data
    @Test
    void sha256_largeStream_noMemoryAmplification() {
        // 10 MB in 8 KB chunks; should not OOM with default heap
        Sha256ChecksumCalculator calc = new Sha256ChecksumCalculator();
        ChecksumComputation comp = calc.newComputation();
        byte[] chunk = new byte[8192];
        int totalChunks = (10 * 1024 * 1024) / 8192;
        for (int i = 0; i < totalChunks; i++) {
            comp.update(chunk, 0, chunk.length);
        }
        String hash = comp.finish();
        assertEquals(64, hash.length()); // SHA-256 hex
    }

}
