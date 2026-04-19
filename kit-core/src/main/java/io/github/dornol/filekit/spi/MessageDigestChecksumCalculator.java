package io.github.dornol.filekit.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * {@link ChecksumCalculator} backed by a configurable {@link MessageDigest}
 * algorithm from {@link ChecksumAlgorithm}.
 *
 * <p>Streaming is supported via both {@link #checksum(InputStream)} (8 KiB
 * buffer) and {@link #newComputation()} (incremental, no buffering).</p>
 *
 * <p>For the common SHA-256 case, {@link Sha256ChecksumCalculator} is a
 * no-arg convenience subclass that simply calls
 * {@code super(ChecksumAlgorithm.SHA_256)}.</p>
 *
 * @since 0.1.20
 */
public class MessageDigestChecksumCalculator implements ChecksumCalculator {

    private static final HexFormat HEX = HexFormat.of();

    private final ChecksumAlgorithm algorithm;

    /**
     * @param algorithm message-digest algorithm to use
     * @throws NullPointerException if {@code algorithm} is null
     */
    public MessageDigestChecksumCalculator(ChecksumAlgorithm algorithm) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    }

    /** Returns the underlying algorithm. */
    public ChecksumAlgorithm algorithm() {
        return algorithm;
    }

    @Override
    public String checksum(byte[] bytes) {
        return HEX.formatHex(newDigest().digest(bytes));
    }

    @Override
    public String checksum(InputStream inputStream) {
        try {
            MessageDigest digest = newDigest();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HEX.formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a streaming computation backed by {@link MessageDigest},
     * avoiding in-memory buffering entirely.
     */
    @Override
    public ChecksumComputation newComputation() {
        return new MessageDigestComputation(newDigest());
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(algorithm.standardName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm.standardName() + " algorithm not available", e);
        }
    }

    private static final class MessageDigestComputation implements ChecksumComputation {
        private final MessageDigest md;
        private boolean finished = false;

        MessageDigestComputation(MessageDigest md) {
            this.md = md;
        }

        @Override
        public void update(byte[] buf, int off, int len) {
            if (finished) {
                throw new IllegalStateException("ChecksumComputation already finished");
            }
            md.update(buf, off, len);
        }

        @Override
        public String finish() {
            if (finished) {
                throw new IllegalStateException("ChecksumComputation already finished");
            }
            finished = true;
            return HEX.formatHex(md.digest());
        }
    }
}
