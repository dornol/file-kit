package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.download.FileDownloadService;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async wrapper around {@link FileDownloadService}. See
 * {@linkplain io.github.dornol.filekit.async package docs} for executor
 * selection, exception propagation, and cancellation semantics.
 *
 * <p><b>Stream caveat:</b> the {@link DownloadResult#content()} stream is
 * consumed on the caller thread, not on the async executor. Reading to EOF
 * (required to finalize checksum verification) blocks the caller. Callers that
 * need fully-async consumption must wrap the read in another
 * {@code supplyAsync} or pipe the stream through an I/O framework of their
 * choice.</p>
 *
 * @since 0.1.16
 */
public final class AsyncFileDownloadService {

    private final FileDownloadService sync;
    private final Executor executor;

    public static Builder builder(FileDownloadService sync) {
        return new Builder(sync);
    }

    private AsyncFileDownloadService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    /** Asynchronously downloads a file. Mirrors {@link FileDownloadService#download}. */
    public CompletableFuture<DownloadResult> downloadAsync(String fileKey) {
        return CompletableFuture.supplyAsync(() -> sync.download(fileKey), executor);
    }

    /** Asynchronously resolves a storage URI. Mirrors {@link FileDownloadService#resolveUri}. */
    public CompletableFuture<String> resolveUriAsync(String fileKey) {
        return CompletableFuture.supplyAsync(() -> sync.resolveUri(fileKey), executor);
    }

    /** Asynchronously generates a pre-signed URL. Mirrors {@link FileDownloadService#generatePresignedUrl}. */
    public CompletableFuture<String> generatePresignedUrlAsync(String fileKey, Duration expiration) {
        return CompletableFuture.supplyAsync(
                () -> sync.generatePresignedUrl(fileKey, expiration), executor);
    }

    public static final class Builder {

        private final FileDownloadService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileDownloadService sync) {
            this.sync = Objects.requireNonNull(sync, "sync");
        }

        /**
         * Sets the executor used to run async operations.
         * See {@link AsyncFileUploadService} class JavaDoc for selection guidance.
         *
         * @throws NullPointerException if {@code executor} is null
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public AsyncFileDownloadService build() {
            return new AsyncFileDownloadService(this);
        }
    }
}
