package io.github.dornol.filekit.async;

import io.github.dornol.filekit.delete.BatchDeleteResult;
import io.github.dornol.filekit.delete.FileDeleteService;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link FileDeleteService}. See
 * {@linkplain io.github.dornol.filekit.async package docs} for executor
 * selection, exception propagation, and cancellation semantics.
 *
 * @since 0.1.17
 */
public final class AsyncFileDeleteService {

    private final FileDeleteService sync;
    private final Executor executor;

    public static Builder builder(FileDeleteService sync) {
        return new Builder(sync);
    }

    private AsyncFileDeleteService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    /**
     * Asynchronously deletes a file. Mirrors {@link FileDeleteService#delete}.
     * The returned future completes with {@code null} on success.
     */
    public CompletableFuture<Void> deleteAsync(String fileKey) {
        return CompletableFuture.runAsync(() -> sync.delete(fileKey), executor);
    }

    /**
     * Asynchronously runs a sequential batch delete. Mirrors
     * {@link FileDeleteService#deleteAll}.
     */
    public CompletableFuture<BatchDeleteResult> deleteAllAsync(Collection<String> fileKeys) {
        return CompletableFuture.supplyAsync(() -> sync.deleteAll(fileKeys), executor);
    }

    public static final class Builder {

        private final FileDeleteService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileDeleteService sync) {
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

        public AsyncFileDeleteService build() {
            return new AsyncFileDeleteService(this);
        }
    }
}
