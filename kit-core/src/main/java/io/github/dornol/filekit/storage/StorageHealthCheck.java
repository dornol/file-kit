package io.github.dornol.filekit.storage;

/** Optional active health probe for a {@link FileStorage} implementation. */
@FunctionalInterface
public interface StorageHealthCheck {

    /**
     * Probes the backend and throws when it is unavailable.
     *
     * @throws RuntimeException when the backend cannot be reached
     */
    void check();
}
