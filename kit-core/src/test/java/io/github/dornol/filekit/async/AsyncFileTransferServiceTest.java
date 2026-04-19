package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.transfer.BatchTransferResult;
import io.github.dornol.filekit.transfer.FileTransferService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncFileTransferServiceTest {

    enum StorageType { LOCAL, S3 }

    private final FileTransferService sync = mock(FileTransferService.class);
    private final FileMetadata metadata = new FileMetadata(
            "new-key", "test.txt", 5, "abc",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("target-bucket", "obj", StorageType.S3));

    // T1
    @Test
    void copyAsync_success() throws Exception {
        when(sync.copy("src", StorageType.S3, "dest")).thenReturn(metadata);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        assertSame(metadata, svc.copyAsync("src", StorageType.S3, "dest").get());
    }

    // T2
    @Test
    void moveAsync_success() throws Exception {
        when(sync.move("src", StorageType.S3, "dest")).thenReturn(metadata);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        assertSame(metadata, svc.moveAsync("src", StorageType.S3, "dest").get());
    }

    // T3
    @Test
    void copyAllAsync_success() throws Exception {
        BatchTransferResult batch = new BatchTransferResult(List.of(metadata), Map.of());
        when(sync.copyAll(any(), any(), any())).thenReturn(batch);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        assertSame(batch, svc.copyAllAsync(List.of("k"), StorageType.S3, "dest").get());
    }

    // T4
    @Test
    void moveAllAsync_success() throws Exception {
        BatchTransferResult batch = new BatchTransferResult(List.of(metadata), Map.of());
        when(sync.moveAll(any(), any(), any())).thenReturn(batch);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        assertSame(batch, svc.moveAllAsync(List.of("k"), StorageType.S3, "dest").get());
    }

    // T5
    @Test
    void copyAsync_exceptionPropagates() {
        FileStorageException fse = new FileStorageException(
                FileStorageException.FILE_NOT_FOUND, "missing");
        when(sync.copy(any(), any(), any())).thenThrow(fse);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.copyAsync("src", StorageType.S3, "dest").get());
        assertSame(fse, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // T6
    @Test
    void builder_nullSync_throws() {
        assertThrows(NullPointerException.class, () -> AsyncFileTransferService.builder(null));
    }

    @Test
    void builder_nullExecutor_throws() {
        assertThrows(NullPointerException.class,
                () -> AsyncFileTransferService.builder(sync).executor(null));
    }

    // T7
    @Test
    void injectedExecutor_runsOnThatExecutor() throws Exception {
        when(sync.copy(any(), any(), any())).thenReturn(metadata);
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "transfer-test-thread");
            t.setDaemon(true);
            return t;
        });
        try {
            AsyncFileTransferService svc = AsyncFileTransferService.builder(sync)
                    .executor(dedicated).build();
            AtomicReference<String> name = new AtomicReference<>();

            CompletableFuture<FileMetadata> cf = svc.copyAsync("src", StorageType.S3, "dest");
            cf.get();
            cf.whenCompleteAsync((m, ex) -> name.set(Thread.currentThread().getName()), dedicated).get();

            assertEquals("transfer-test-thread", name.get());
        } finally {
            dedicated.shutdownNow();
        }
    }
}
