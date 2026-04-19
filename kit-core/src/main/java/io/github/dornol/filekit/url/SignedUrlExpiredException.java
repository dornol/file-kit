package io.github.dornol.filekit.url;

/**
 * Thrown by {@link SignedUrlSigner#verify} when the {@code exp} component
 * is before the signer's current {@code Clock} instant.
 *
 * @since 0.1.23
 */
public class SignedUrlExpiredException extends SignedUrlException {

    private static final long serialVersionUID = 1L;

    public SignedUrlExpiredException(String message) {
        super(message);
    }
}
