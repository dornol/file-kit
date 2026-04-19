package io.github.dornol.filekit.url;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedUrlSignerTest {

    private static final byte[] SECRET =
            "test-secret-key-for-signing-1234".getBytes();
    private static final Instant FIXED_NOW =
            Instant.parse("2026-04-19T12:00:00Z");

    private SignedUrlSigner newSigner() {
        return new SignedUrlSigner(SECRET, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private static long[] parseFragment(String fragment) {
        // "exp=NNN&sig=..."
        String[] parts = fragment.split("&", 2);
        long exp = Long.parseLong(parts[0].substring("exp=".length()));
        return new long[]{exp};
    }

    private static String sigFromFragment(String fragment) {
        int idx = fragment.indexOf("&sig=");
        return fragment.substring(idx + "&sig=".length());
    }

    // S1
    @Test
    void signAndVerify_roundtrip() {
        SignedUrlSigner signer = newSigner();
        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));

        long exp = parseFragment(fragment)[0];
        String sig = sigFromFragment(fragment);

        assertDoesNotThrow(() -> signer.verify("file-123", exp, sig));
    }

    // S2
    @Test
    void expired_throws() {
        SignedUrlSigner signer = newSigner();
        long pastExp = FIXED_NOW.minus(Duration.ofSeconds(1)).getEpochSecond();
        // produce a valid sig for the past exp
        String fragment = signer.sign("file-123",
                Instant.ofEpochSecond(pastExp));
        String sig = sigFromFragment(fragment);

        assertThrows(SignedUrlExpiredException.class,
                () -> signer.verify("file-123", pastExp, sig));
    }

    // S3
    @Test
    void tamperedSignature_throwsInvalidSignature() {
        SignedUrlSigner signer = newSigner();
        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));
        long exp = parseFragment(fragment)[0];
        String sig = sigFromFragment(fragment);
        String tampered = "A" + sig.substring(1); // flip one char

        assertThrows(SignedUrlInvalidSignatureException.class,
                () -> signer.verify("file-123", exp, tampered));
    }

    // S4
    @Test
    void differentFileKey_throwsInvalidSignature() {
        SignedUrlSigner signer = newSigner();
        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));
        long exp = parseFragment(fragment)[0];
        String sig = sigFromFragment(fragment);

        assertThrows(SignedUrlInvalidSignatureException.class,
                () -> signer.verify("file-456", exp, sig));
    }

    // S5
    @Test
    void differentExp_throwsInvalidSignature() {
        SignedUrlSigner signer = newSigner();
        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));
        String sig = sigFromFragment(fragment);
        long fakeExp = FIXED_NOW.plus(Duration.ofHours(2)).getEpochSecond();

        assertThrows(SignedUrlInvalidSignatureException.class,
                () -> signer.verify("file-123", fakeExp, sig));
    }

    // S6
    @Test
    void malformedBase64_throwsInvalidSignature() {
        SignedUrlSigner signer = newSigner();
        long exp = FIXED_NOW.plus(Duration.ofHours(1)).getEpochSecond();

        assertThrows(SignedUrlInvalidSignatureException.class,
                () -> signer.verify("file-123", exp, "!!!not-base64!!!"));
    }

    // S7
    @Test
    void constructor_nullSecret_throws() {
        assertThrows(NullPointerException.class, () -> new SignedUrlSigner(null));
    }

    // S8
    @Test
    void constructor_shortSecret_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SignedUrlSigner(new byte[15]));
    }

    // S8b — exactly 16 bytes allowed
    @Test
    void constructor_minLengthSecret_ok() {
        assertDoesNotThrow(() -> new SignedUrlSigner(new byte[16]));
    }

    // S9
    @Test
    void constructor_nullClock_throws() {
        assertThrows(NullPointerException.class,
                () -> new SignedUrlSigner(SECRET, null));
    }

    // S10
    @Test
    void sign_nullArgs_throw() {
        SignedUrlSigner signer = newSigner();
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> signer.sign(null, FIXED_NOW)),
                () -> assertThrows(NullPointerException.class,
                        () -> signer.sign("key", null))
        );
    }

    // S11
    @Test
    void verify_nullArgs_throw() {
        SignedUrlSigner signer = newSigner();
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> signer.verify(null, 0L, "sig")),
                () -> assertThrows(NullPointerException.class,
                        () -> signer.verify("key", 0L, null))
        );
    }

    // S12
    @Test
    void clockInjection_controlsExpiry() {
        // Use a fake clock that jumps forward 2 hours between sign and verify
        MutableClock clock = new MutableClock(FIXED_NOW);
        SignedUrlSigner signer = new SignedUrlSigner(SECRET, clock);

        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));
        long exp = parseFragment(fragment)[0];
        String sig = sigFromFragment(fragment);

        // at sign time → valid
        assertDoesNotThrow(() -> signer.verify("file-123", exp, sig));

        // advance clock past exp
        clock.now = FIXED_NOW.plus(Duration.ofHours(2));
        assertThrows(SignedUrlExpiredException.class,
                () -> signer.verify("file-123", exp, sig));
    }

    // S13
    @Test
    void fragmentFormat_hasExpAndSig() {
        SignedUrlSigner signer = newSigner();
        String fragment = signer.sign("file-123", FIXED_NOW.plus(Duration.ofHours(1)));

        assertTrue(fragment.startsWith("exp="));
        assertTrue(fragment.contains("&sig="));
    }

    // --- helper ---

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
    }
}
