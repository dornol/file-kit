package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.upload.BatchUploadResult;
import io.github.dornol.filekit.upload.FileUploadService;
import io.github.dornol.filekit.upload.UploadCallback;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link FileUploadService}. Each public method is
 * exposed as a {@link CompletableFuture}-returning variant, submitting work to
 * a configurable {@link Executor}. See {@linkplain io.github.dornol.filekit.async
 * package docs} for executor selection, exception propagation, and cancellation
 * semantics.
 *
 * <p><b>Batch note:</b> {@link #uploadAllAsync} preserves the sequential
 * semantics of {@link FileUploadService#uploadAll}. A future parallel variant
 * would require separate design (e.g. per-file dedup ordering).
 *
 * @since 0.1.16
 */
public final class AsyncFileUploadService {

    private final FileUploadService sync;
    private final Executor executor;

    public static Builder builder(FileUploadService sync) {
        return new Builder(sync);
    }

    private AsyncFileUploadService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    /**
     * Asynchronously uploads a file. Mirrors {@link FileUploadService#upload(FileSource, Enum, String)}.
     *
     * <p>A checked {@link IOException} from the sync call is wrapped in
     * {@link CompletionException}; unwrap via {@code ex.getCause()} on the
     * consumer side.</p>
     */
    public CompletableFuture<FileMetadata> uploadAsync(
            FileSource fileSource, Enum<?> storageType, String bucket) {
        return supplyIO(() -> sync.upload(fileSource, storageType, bucket));
    }

    /**
     * Asynchronously uploads a file and runs a callback before metadata save.
     * Mirrors {@link FileUploadService#upload(FileSource, Enum, String, UploadCallback)}.
     */
    public CompletableFuture<FileMetadata> uploadAsync(
            FileSource fileSource, Enum<?> storageType, String bucket,
            UploadCallback callback) {
        return supplyIO(() -> sync.upload(fileSource, storageType, bucket, callback));
    }

    /**
     * Asynchronously runs the sequential batch upload. Mirrors
     * {@link FileUploadService#uploadAll}. Individual uploads are executed
     * sequentially on the configured executor (no cross-upload parallelism).
     */
    public CompletableFuture<BatchUploadResult> uploadAllAsync(
            Collection<? extends FileSource> fileSources,
            Enum<?> storageType, String bucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.uploadAll(fileSources, storageType, bucket),
                executor);
    }

    private <T> CompletableFuture<T> supplyIO(IOSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    public static final class Builder {

        private final FileUploadService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileUploadService sync) {
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

        public AsyncFileUploadService build() {
            return new AsyncFileUploadService(this);
        }
    }
}
