package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChecksumAlgorithmTest {

    // A1
    @Test
    void allStandardNames_resolvableViaMessageDigest() {
        for (ChecksumAlgorithm alg : ChecksumAlgorithm.values()) {
            assertDoesNotThrow(() -> MessageDigest.getInstance(alg.standardName()),
                    "algorithm " + alg + " (" + alg.standardName() + ") must be available");
        }
    }

    // A2
    @Test
    void values_contains5Algorithms() {
        assertEquals(5, ChecksumAlgorithm.values().length);
    }

    // A3
    @Test
    void standardName_notNull() {
        for (ChecksumAlgorithm alg : ChecksumAlgorithm.values()) {
            assertNotNull(alg.standardName());
        }
    }

    // Explicit name checks for stability
    @Test
    void standardNames_matchJcaConvention() {
        assertEquals("MD5", ChecksumAlgorithm.MD5.standardName());
        assertEquals("SHA-1", ChecksumAlgorithm.SHA_1.standardName());
        assertEquals("SHA-256", ChecksumAlgorithm.SHA_256.standardName());
        assertEquals("SHA-384", ChecksumAlgorithm.SHA_384.standardName());
        assertEquals("SHA-512", ChecksumAlgorithm.SHA_512.standardName());
    }
}
