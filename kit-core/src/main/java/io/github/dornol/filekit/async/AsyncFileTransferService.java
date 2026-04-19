package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.transfer.BatchTransferResult;
import io.github.dornol.filekit.transfer.FileTransferService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Asynchronously copies multiple files in parallel. Each source key is
     * submitted as an independent task on the configured executor; effective
     * parallelism is therefore bounded by the executor's concurrency (e.g. a
     * fixed-size pool caps fan-out; a virtual-thread executor effectively
     * does not).
     *
     * <p><b>Ordering:</b> the returned {@link BatchTransferResult#succeeded()}
     * list is NOT guaranteed to match the input iteration order.</p>
     *
     * <p>Individual copy failures do not fail the returned future — they are
     * collected into {@link BatchTransferResult#failed()} keyed by the input
     * source key. Failure messages mirror the sync batch format (underlying
     * cause message, with class simple-name fallback for null messages).
     * An empty {@code fileKeys} collection yields an immediately-complete
     * future with empty {@code succeeded} / {@code failed}.</p>
     *
     * @since 0.1.21
     */
    public CompletableFuture<BatchTransferResult> copyAllParallelAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return allInParallel(fileKeys,
                key -> copyAsync(key, targetStorageType, targetBucket));
    }

    /**
     * Asynchronously moves multiple files in parallel. See
     * {@link #copyAllParallelAsync} for parallelism, ordering, empty-input,
     * and failure-collection semantics.
     *
     * @since 0.1.21
     */
    public CompletableFuture<BatchTransferResult> moveAllParallelAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return allInParallel(fileKeys,
                key -> moveAsync(key, targetStorageType, targetBucket));
    }

    private CompletableFuture<BatchTransferResult> allInParallel(
            Collection<String> fileKeys,
            java.util.function.Function<String, CompletableFuture<FileMetadata>> op) {
        Objects.requireNonNull(fileKeys, "fileKeys");
        List<CompletableFuture<Entry>> futures = fileKeys.stream()
                .map(key -> op.apply(key).<Entry>handle((metadata, ex) -> ex == null
                        ? new Entry(key, metadata, null)
                        : new Entry(key, null, AsyncInternal.unwrapMessage(ex))))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> {
                    List<FileMetadata> succeeded = new ArrayList<>();
                    Map<String, String> failed = new LinkedHashMap<>();
                    for (CompletableFuture<Entry> f : futures) {
                        Entry e = f.join();
                        if (e.failureReason == null) {
                            succeeded.add(e.metadata);
                        } else {
                            failed.put(e.key, e.failureReason);
                        }
                    }
                    return new BatchTransferResult(succeeded, failed);
                });
    }

    private record Entry(String key, FileMetadata metadata, String failureReason) {}

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
