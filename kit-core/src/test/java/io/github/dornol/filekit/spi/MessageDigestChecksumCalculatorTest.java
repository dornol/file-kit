package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageDigestChecksumCalculatorTest {

    private static final byte[] DATA = "hello world".getBytes();

    // M1
    @Test
    void sha256_byteArray_matchesKnownHash() throws Exception {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(DATA));
        assertEquals(expected, calc.checksum(DATA));
    }

    // M2
    @Test
    void md5_byteArray_matchesKnownHash() throws Exception {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.MD5);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("MD5").digest(DATA));
        assertEquals(expected, calc.checksum(DATA));
    }

    // M3
    @Test
    void sha512_byteArray_matchesKnownHash() throws Exception {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_512);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(DATA));
        assertEquals(expected, calc.checksum(DATA));
    }

    // M4
    @Test
    void inputStreamChecksum_equalsByteArrayChecksum() {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);
        assertEquals(calc.checksum(DATA),
                calc.checksum(new ByteArrayInputStream(DATA)));
    }

    // M5
    @Test
    void streamingComputation_equalsByteArrayChecksum() {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);

        ChecksumComputation comp = calc.newComputation();
        comp.update(DATA, 0, 5);
        comp.update(DATA, 5, DATA.length - 5);
        String streamed = comp.finish();

        assertEquals(calc.checksum(DATA), streamed);
    }

    // M6
    @Test
    void constructor_nullAlgorithm_throws() {
        assertThrows(NullPointerException.class,
                () -> new MessageDigestChecksumCalculator(null));
    }

    // M7
    @Test
    void algorithm_returnsConfigured() {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_384);
        assertEquals(ChecksumAlgorithm.SHA_384, calc.algorithm());
    }

    // M8
    @Test
    void emptyInput_returnsAlgorithmEmptyHash() throws Exception {
        MessageDigestChecksumCalculator calc =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        assertEquals(expected, calc.checksum(new byte[0]));
    }

    // Sanity — Sha256ChecksumCalculator (subclass) matches directly-instantiated SHA_256
    @Test
    void sha256Subclass_equivalentToExplicitAlgorithm() {
        Sha256ChecksumCalculator sub = new Sha256ChecksumCalculator();
        MessageDigestChecksumCalculator direct =
                new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);
        assertEquals(direct.checksum(DATA), sub.checksum(DATA));
    }
}
