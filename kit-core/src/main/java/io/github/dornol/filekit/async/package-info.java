/**
 * Async adapters wrapping the synchronous services in the kit-core root
 * packages. Each adapter exposes {@link java.util.concurrent.CompletableFuture}-returning
 * variants of the public methods and runs the underlying sync call on a
 * configurable {@link java.util.concurrent.Executor}.
 *
 * <h2>Executor selection</h2>
 * The default is {@link java.util.concurrent.ForkJoinPool#commonPool()}, which
 * is shared with ambient stream / parallel work — submitting blocking file I/O
 * there can starve other tasks. Production code should inject a dedicated
 * executor. On JDK 21+, prefer virtual threads:
 * <pre>{@code
 * AsyncFileUploadService asyncUpload = AsyncFileUploadService.builder(sync)
 *     .executor(Executors.newVirtualThreadPerTaskExecutor())
 *     .build();
 * }</pre>
 *
 * <h2>Exception propagation</h2>
 * Checked {@link java.io.IOException}s from the sync services are wrapped in
 * {@link java.util.concurrent.CompletionException} per {@code CompletableFuture}
 * conventions. Unchecked exceptions (e.g. {@code FileStorageException}) surface
 * as the cause of the {@code CompletionException} delivered to consumers:
 * <pre>{@code
 * asyncUpload.uploadAsync(src, type, bucket)
 *     .exceptionally(ex -> {
 *         Throwable cause = ex.getCause();
 *         if (cause instanceof FileStorageException fse) { ... }
 *         return null;
 *     });
 * }</pre>
 *
 * <h2>Cancellation</h2>
 * {@link java.util.concurrent.CompletableFuture#cancel} does not interrupt
 * in-flight I/O; it only marks the future cancelled. Work already submitted
 * to the executor runs to completion.
 *
 * <p>The adapters introduce no new runtime dependencies — only JDK types.</p>
 *
 * @since 0.1.16
 */
package io.github.dornol.filekit.async;
