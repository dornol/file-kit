package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        String a = calculator.checksum("a".getBytes());
        String b = calculator.checksum("b".getBytes());
        assertNotNull(a);
        assertNotNull(b);
        assert !a.equals(b);
    }

}
