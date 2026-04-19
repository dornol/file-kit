package io.github.dornol.filekit.url;

/**
 * Base class for signed-URL verification errors produced by
 * {@link SignedUrlSigner}. Unchecked (extends {@link RuntimeException})
 * to keep verify call sites concise.
 *
 * @since 0.1.23
 */
public class SignedUrlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SignedUrlException(String message) {
        super(message);
    }
}
