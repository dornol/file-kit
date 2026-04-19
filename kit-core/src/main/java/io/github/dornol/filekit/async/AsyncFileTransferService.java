package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.transfer.BatchTransferResult;
import io.github.dornol.filekit.transfer.FileTransferService;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link FileTransferService}. See
 * {@linkplain io.github.dornol.filekit.async package docs} for executor
 * selection, exception propagation, and cancellation semantics.
 *
 * <p><b>Batch note:</b> {@link #copyAllAsync} / {@link #moveAllAsync} preserve
 * the sequential semantics of the underlying sync methods.</p>
 *
 * @since 0.1.17
 */
public final class AsyncFileTransferService {

    private final FileTransferService sync;
    private final Executor executor;

    public static Builder builder(FileTransferService sync) {
        return new Builder(sync);
    }

    private AsyncFileTransferService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    /** Asynchronously copies a file. Mirrors {@link FileTransferService#copy}. */
    public CompletableFuture<FileMetadata> copyAsync(
            String fileKey, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.copy(fileKey, targetStorageType, targetBucket), executor);
    }

    /** Asynchronously moves a file. Mirrors {@link FileTransferService#move}. */
    public CompletableFuture<FileMetadata> moveAsync(
            String fileKey, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.move(fileKey, targetStorageType, targetBucket), executor);
    }

    /** Asynchronously copies multiple files sequentially. Mirrors {@link FileTransferService#copyAll}. */
    public CompletableFuture<BatchTransferResult> copyAllAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.copyAll(fileKeys, targetStorageType, targetBucket), executor);
    }

    /** Asynchronously moves multiple files sequentially. Mirrors {@link FileTransferService#moveAll}. */
    public CompletableFuture<BatchTransferResult> moveAllAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.moveAll(fileKeys, targetStorageType, targetBucket), executor);
    }

    public static final class Builder {

        private final FileTransferService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileTransferService sync) {
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

        public AsyncFileTransferService build() {
            return new AsyncFileTransferService(this);
        }
    }
}
