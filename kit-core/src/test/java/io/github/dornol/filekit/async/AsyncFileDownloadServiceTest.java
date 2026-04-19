package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncFileDownloadServiceTest {

    enum StorageType { LOCAL }

    private final FileDownloadService sync = mock(FileDownloadService.class);
    private final FileMetadata metadata = new FileMetadata(
            "key", "test.txt", 5, "abc",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL));

    // D1
    @Test
    void downloadAsync_success() throws Exception {
        DownloadResult dr = new DownloadResult(metadata, new ByteArrayInputStream("hi".getBytes()));
        when(sync.download("key")).thenReturn(dr);

        AsyncFileDownloadService svc = AsyncFileDownloadService.builder(sync).build();

        DownloadResult result = svc.downloadAsync("key").get();

        assertSame(dr, result);
    }

    // D2
    @Test
    void downloadAsync_notFound_propagatesAsCause() {
        FileStorageException fse = new FileStorageException(
                FileStorageException.FILE_NOT_FOUND, "missing");
        when(sync.download("missing")).thenThrow(fse);

        AsyncFileDownloadService svc = AsyncFileDownloadService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.downloadAsync("missing").get());

        assertSame(fse, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // D3
    @Test
    void resolveUriAsync_success() throws Exception {
        when(sync.resolveUri("key")).thenReturn("https://example/file");

        AsyncFileDownloadService svc = AsyncFileDownloadService.builder(sync).build();

        assertEquals("https://example/file", svc.resolveUriAsync("key").get());
    }

    // D4
    @Test
    void generatePresignedUrlAsync_passesExpirationThrough() throws Exception {
        Duration d = Duration.ofHours(2);
        when(sync.generatePresignedUrl("key", d)).thenReturn("https://presigned");

        AsyncFileDownloadService svc = AsyncFileDownloadService.builder(sync).build();

        assertEquals("https://presigned", svc.generatePresignedUrlAsync("key", d).get());
    }

    // D5
    @Test
    void builder_nullSync_throws() {
        assertThrows(NullPointerException.class, () -> AsyncFileDownloadService.builder(null));
    }

    // D6
    @Test
    void builder_nullExecutor_throws() {
        assertThrows(NullPointerException.class,
                () -> AsyncFileDownloadService.builder(sync).executor(null));
    }

    // D7
    @Test
    void injectedExecutor_runsOnThatExecutor() throws Exception {
        when(sync.resolveUri("key")).thenReturn("uri");
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "file-kit-async-download-test");
            t.setDaemon(true);
            return t;
        });
        try {
            AsyncFileDownloadService svc = AsyncFileDownloadService.builder(sync)
                    .executor(dedicated)
                    .build();
            AtomicReference<String> name = new AtomicReference<>();

            CompletableFuture<String> cf = svc.resolveUriAsync("key");
            cf.get();
            cf.whenCompleteAsync((s, ex) -> name.set(Thread.currentThread().getName()), dedicated).get();

            assertEquals("file-kit-async-download-test", name.get());
        } finally {
            dedicated.shutdownNow();
        }
    }

}
