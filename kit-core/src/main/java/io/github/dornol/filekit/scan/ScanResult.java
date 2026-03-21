package io.github.dornol.filekit.scan;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Result of a virus scan operation.
 *
 * @param status  the scan outcome
 * @param message optional detail message (e.g. virus name for INFECTED, error detail for ERROR)
 */
public record ScanResult(Status status, @Nullable String message) {

    public ScanResult {
        Objects.requireNonNull(status, "status");
    }

    public enum Status {
        CLEAN,
        INFECTED,
        ERROR
    }

    /**
     * Returns a CLEAN result with no message.
     */
    public static ScanResult clean() {
        return new ScanResult(Status.CLEAN, null);
    }

    /**
     * Returns an INFECTED result with the given detail message.
     */
    public static ScanResult infected(String message) {
        return new ScanResult(Status.INFECTED, message);
    }

    /**
     * Returns an ERROR result with the given detail message.
     */
    public static ScanResult error(String message) {
        return new ScanResult(Status.ERROR, message);
    }
}
