package io.github.dornol.filekit.spi;

/**
 * Default {@link ChecksumCalculator} implementation using SHA-256.
 *
 * <p>Thin convenience subclass of {@link MessageDigestChecksumCalculator}.
 * For other algorithms, use {@code new MessageDigestChecksumCalculator(ChecksumAlgorithm.X)}
 * directly.</p>
 */
public class Sha256ChecksumCalculator extends MessageDigestChecksumCalculator {

    public Sha256ChecksumCalculator() {
        super(ChecksumAlgorithm.SHA_256);
    }
}
