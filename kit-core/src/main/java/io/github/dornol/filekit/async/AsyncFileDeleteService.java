package io.github.dornol.filekit.async;

import io.github.dornol.filekit.delete.BatchDeleteResult;
import io.github.dornol.filekit.delete.FileDeleteService;

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

    /**
     * Asynchronously deletes multiple files in parallel. Each key is submitted
     * as an independent task on the configured executor; effective parallelism
     * is therefore bounded by the executor's concurrency.
     *
     * <p><b>Ordering:</b> the returned {@link BatchDeleteResult#succeeded()}
     * list is NOT guaranteed to match the input iteration order.</p>
     *
     * <p>Individual delete failures do not fail the returned future — they
     * are collected into {@link BatchDeleteResult#failed()} keyed by file
     * key. Failure messages mirror the sync batch format (underlying cause
     * message, with class simple-name fallback for null messages).
     * An empty {@code fileKeys} collection yields an immediately-complete
     * future with empty {@code succeeded} / {@code failed}.</p>
     *
     * @since 0.1.21
     */
    public CompletableFuture<BatchDeleteResult> deleteAllParallelAsync(Collection<String> fileKeys) {
        Objects.requireNonNull(fileKeys, "fileKeys");
        List<CompletableFuture<Entry>> futures = fileKeys.stream()
                .map(key -> deleteAsync(key).<Entry>handle((unused, ex) -> ex == null
                        ? new Entry(key, null)
                        : new Entry(key, AsyncInternal.unwrapMessage(ex))))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> {
                    List<String> succeeded = new ArrayList<>();
                    Map<String, String> failed = new LinkedHashMap<>();
                    for (CompletableFuture<Entry> f : futures) {
                        Entry e = f.join();
                        if (e.failureReason == null) {
                            succeeded.add(e.key);
                        } else {
                            failed.put(e.key, e.failureReason);
                        }
                    }
                    return new BatchDeleteResult(succeeded, failed);
                });
    }

    private record Entry(String key, String failureReason) {}

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
