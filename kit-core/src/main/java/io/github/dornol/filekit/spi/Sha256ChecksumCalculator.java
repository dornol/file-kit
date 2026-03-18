package io.github.dornol.filekit.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Default {@link ChecksumCalculator} implementation using SHA-256.
 */
public class Sha256ChecksumCalculator implements ChecksumCalculator {

    /**
     * Computes a SHA-256 checksum for the given byte array.
     *
     * @param bytes file content
     * @return lowercase hex-encoded SHA-256 hash
     */
    @Override
    public String checksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a SHA-256 checksum by streaming from the given {@link InputStream}.
     *
     * <p>Reads the stream in 8 KB chunks to avoid loading the entire file into memory.</p>
     *
     * @param inputStream file content stream (not closed by this method)
     * @return lowercase hex-encoded SHA-256 hash
     */
    @Override
    public String checksum(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
