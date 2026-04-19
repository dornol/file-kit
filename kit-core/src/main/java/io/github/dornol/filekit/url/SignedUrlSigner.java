package io.github.dornol.filekit.url;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * HMAC-SHA256 signer/verifier for time-limited download URLs.
 *
 * <p>Produces a query-fragment string of the form
 * {@code "exp={epochSeconds}&sig={base64url-signature}"} which the caller can
 * append to any URL for its own local-storage download endpoint. The
 * companion {@link #verify} method checks the signature and expiration.</p>
 *
 * <p><b>Scope of this helper:</b>
 * <ul>
 *   <li>Does the cryptography — HMAC-SHA256, URL-safe Base64,
 *       constant-time comparison — correctly and once.</li>
 *   <li>Does <em>not</em> assemble full URLs, resolve file metadata, or
 *       authorize users. Authorization remains the application's
 *       responsibility: a valid signature proves the URL came from your
 *       app; whether <em>this user</em> is allowed to download
 *       <em>this file</em> is still up to the caller to check after
 *       {@code verify} succeeds.</li>
 * </ul>
 *
 * <p>Signing payload is {@code fileKey + "|" + epochSeconds}. The separator
 * binds the signature to the specific file key; tampering with either
 * component invalidates the signature. File keys are expected to be UUID
 * strings (kit-core's convention); embedding {@code '|'} in a file key is
 * not supported and would allow forging across keys that share a prefix.</p>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // app startup
 * SignedUrlSigner signer = new SignedUrlSigner(secretBytes);
 *
 * // issue
 * String fragment = signer.sign(meta.key(), Instant.now().plus(Duration.ofHours(1)));
 * String url = "https://files.example.com/download?key=" + meta.key() + "&" + fragment;
 *
 * // serve
 * try {
 *     signer.verify(req.getParameter("key"),
 *                   Long.parseLong(req.getParameter("exp")),
 *                   req.getParameter("sig"));
 *     // app-level authorization here, then serve the file
 * } catch (SignedUrlExpiredException e) { return HTTP_410; }
 *   catch (SignedUrlInvalidSignatureException e) { return HTTP_403; }
 * }</pre>
 *
 * @since 0.1.23
 */
public final class SignedUrlSigner {

    private static final String ALGORITHM = "HmacSHA256";
    /** Minimum secret length recommended for HMAC-SHA256 (128 bits). */
    private static final int MIN_SECRET_LEN = 16;
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final SecretKey secretKey;
    private final Clock clock;

    /**
     * Creates a signer using the system UTC clock.
     *
     * @param secret HMAC key bytes; must be at least 16 bytes
     * @throws NullPointerException     if {@code secret} is null
     * @throws IllegalArgumentException if {@code secret.length < 16}
     */
    public SignedUrlSigner(byte[] secret) {
        this(secret, Clock.systemUTC());
    }

    /**
     * Creates a signer using the given clock. Primarily for testing; most
     * callers use {@link #SignedUrlSigner(byte[])}.
     *
     * @param secret HMAC key bytes; must be at least 16 bytes
     * @param clock  clock used for expiration comparison
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if {@code secret.length < 16}
     */
    public SignedUrlSigner(byte[] secret, Clock clock) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < MIN_SECRET_LEN) {
            throw new IllegalArgumentException(
                    "secret must be at least " + MIN_SECRET_LEN + " bytes, got " + secret.length);
        }
        this.secretKey = new SecretKeySpec(secret, ALGORITHM);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Signs a fileKey with an expiration time and returns a URL-safe query
     * fragment {@code "exp={epochSeconds}&sig={base64url-signature}"}.
     */
    public String sign(String fileKey, Instant expiration) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(expiration, "expiration");
        long exp = expiration.getEpochSecond();
        byte[] sig = hmac(fileKey + "|" + exp);
        return "exp=" + exp + "&sig=" + ENC.encodeToString(sig);
    }

    /**
     * Verifies the signature against the given fileKey and expiration.
     *
     * @throws SignedUrlExpiredException           if {@code exp} is before the signer's current time
     * @throws SignedUrlInvalidSignatureException  if {@code sigBase64} does not match the expected HMAC
     *                                             (or is malformed Base64)
     */
    public void verify(String fileKey, long exp, String sigBase64) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(sigBase64, "sigBase64");
        Instant expInstant = Instant.ofEpochSecond(exp);
        if (expInstant.isBefore(clock.instant())) {
            throw new SignedUrlExpiredException("signed URL expired at " + expInstant);
        }
        byte[] expected = hmac(fileKey + "|" + exp);
        byte[] actual;
        try {
            actual = DEC.decode(sigBase64);
        } catch (IllegalArgumentException e) {
            throw new SignedUrlInvalidSignatureException("malformed signature");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SignedUrlInvalidSignatureException("signature mismatch");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
