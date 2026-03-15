package io.github.dornol.filekit.spi;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Default {@link ChecksumCalculator} implementation using SHA-256.
 */
public class Sha256ChecksumCalculator implements ChecksumCalculator {

    @Override
    public String checksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
