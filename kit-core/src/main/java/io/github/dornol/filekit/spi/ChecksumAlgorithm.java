package io.github.dornol.filekit.spi;

/**
 * Curated list of message-digest algorithms supported by
 * {@link MessageDigestChecksumCalculator}.
 *
 * <p>Each constant maps to the standard algorithm name recognized by
 * {@link java.security.MessageDigest#getInstance(String)}. Using the enum
 * avoids typos (e.g. {@code "SHA256"} vs {@code "SHA-256"}).</p>
 *
 * <p>MD5 and SHA-1 are retained for legacy-compatibility use cases — they are
 * <b>not</b> recommended for security-sensitive deduplication. Prefer
 * {@link #SHA_256} or stronger for new code.</p>
 *
 * @since 0.1.20
 */
public enum ChecksumAlgorithm {

    MD5("MD5"),
    SHA_1("SHA-1"),
    SHA_256("SHA-256"),
    SHA_384("SHA-384"),
    SHA_512("SHA-512");

    private final String standardName;

    ChecksumAlgorithm(String standardName) {
        this.standardName = standardName;
    }

    /**
     * Returns the standard algorithm name recognized by
     * {@link java.security.MessageDigest#getInstance(String)}.
     */
    public String standardName() {
        return standardName;
    }
}
