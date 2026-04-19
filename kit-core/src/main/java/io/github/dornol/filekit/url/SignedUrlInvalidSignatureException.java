package io.github.dornol.filekit.url;

/**
 * Thrown by {@link SignedUrlSigner#verify} when the supplied signature
 * does not match the expected HMAC (or is malformed Base64).
 *
 * <p>The message is intentionally generic to avoid leaking information
 * useful for forgery attempts; callers should map this to an HTTP 403
 * response without echoing details.</p>
 *
 * @since 0.1.23
 */
public class SignedUrlInvalidSignatureException extends SignedUrlException {

    private static final long serialVersionUID = 1L;

    public SignedUrlInvalidSignatureException(String message) {
        super(message);
    }
}
