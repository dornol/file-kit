package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.metadata.FileRenameService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link FileRenameService}. See
 * {@linkplain io.github.dornol.filekit.async package docs} for executor
 * selection, exception propagation, and cancellation semantics.
 *
 * @since 0.1.17
 */
public final class AsyncFileRenameService {

    private final FileRenameService sync;
    private final Executor executor;

    public static Builder builder(FileRenameService sync) {
        return new Builder(sync);
    }

    private AsyncFileRenameService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    /** Asynchronously renames a file. Mirrors {@link FileRenameService#rename}. */
    public CompletableFuture<FileMetadata> renameAsync(String fileKey, String newName) {
        return CompletableFuture.supplyAsync(() -> sync.rename(fileKey, newName), executor);
    }

    public static final class Builder {

        private final FileRenameService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileRenameService sync) {
            this.sync = Objects.requireNonNull(sync, "sync");
        }

        /**
         * Sets the executor used to run async operations. See
         * {@linkplain io.github.dornol.filekit.async package docs} for
         * selection guidance.
         *
         * @throws NullPointerException if {@code executor} is null
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public AsyncFileRenameService build() {
            return new AsyncFileRenameService(this);
        }
    }
}
