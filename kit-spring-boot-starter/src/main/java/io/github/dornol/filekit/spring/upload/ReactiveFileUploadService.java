package io.github.dornol.filekit.spring.upload;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spring.validator.FilePartSource;
import io.github.dornol.filekit.upload.FileUploadService;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Objects;

/**
 * Reactive adapter for the blocking core upload service.
 * Blocking file I/O is scheduled on Reactor's bounded-elastic scheduler and
 * the temporary {@link FilePartSource} is cleaned up on success, failure, or cancellation.
 */
public final class ReactiveFileUploadService {

    private final FileUploadService delegate;
    private final long maxUploadSize;

    public ReactiveFileUploadService(FileUploadService delegate, long maxUploadSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxUploadSize < 0) {
            throw new IllegalArgumentException("maxUploadSize must not be negative");
        }
        this.maxUploadSize = maxUploadSize;
    }

    /** Uploads a WebFlux file part and cleans its temporary buffer afterward. */
    public Mono<FileMetadata> upload(FilePart filePart, Enum<?> storageType, String bucket) {
        Objects.requireNonNull(filePart, "filePart");
        return FilePartSource.from(filePart, maxUploadSize)
                .flatMap(source -> Mono.using(
                        () -> source,
                        value -> Mono.fromCallable(() -> delegate.upload(value, storageType, bucket))
                                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()),
                        value -> closeQuietly(value)
                ));
    }

    private static void closeQuietly(FilePartSource source) {
        try {
            source.close();
        } catch (IOException ignored) {
            // best-effort cleanup; the upload result is already determined
        }
    }
}
