package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adapter that wraps a Spring WebFlux {@link FilePart} as a {@link FileSource}.
 *
 * <p>Because {@code FilePart.content()} returns a {@code Flux<DataBuffer>} that can only
 * be consumed once, this adapter buffers the content to a temporary file via
 * {@link FilePart#transferTo(Path)}. The resulting {@code FilePartSource} supports
 * multiple calls to {@link #getInputStream()}.</p>
 *
 * <p>Implements {@link Closeable} to clean up the temporary file. Use with
 * try-with-resources:</p>
 * <pre>{@code
 * FilePartSource.from(filePart)
 *     .flatMap(source -> Mono.fromCallable(() -> {
 *         try (source) {
 *             return uploadService.upload(source, storageType, bucket);
 *         }
 *     }).subscribeOn(Schedulers.boundedElastic()));
 * }</pre>
 */
public class FilePartSource implements FileSource, Closeable {

    private final String filename;
    private final Path tempFile;
    private final long size;

    private FilePartSource(String filename, Path tempFile, long size) {
        this.filename = filename;
        this.tempFile = tempFile;
        this.size = size;
    }

    /**
     * Creates a {@code FilePartSource} by buffering the {@link FilePart} content
     * to a temporary file.
     *
     * @param filePart the WebFlux file part to wrap
     * @return a {@code Mono} that emits the buffered {@code FilePartSource}
     */
    public static Mono<FilePartSource> from(FilePart filePart) {
        return Mono.fromCallable(() -> Files.createTempFile("file-kit-", ".tmp"))
                .flatMap(temp -> filePart.transferTo(temp)
                        .then(Mono.fromCallable(() -> {
                            long size = Files.size(temp);
                            return new FilePartSource(filePart.filename(), temp, size);
                        }))
                        .onErrorResume(e -> Mono.fromRunnable(() -> deleteSilently(temp))
                                .then(Mono.error(e))));
    }

    private static void deleteSilently(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    @Override
    public @Nullable String getOriginalFilename() {
        return filename;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(tempFile);
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Deletes the temporary file. Safe to call multiple times.
     */
    @Override
    public void close() throws IOException {
        Files.deleteIfExists(tempFile);
    }

}
