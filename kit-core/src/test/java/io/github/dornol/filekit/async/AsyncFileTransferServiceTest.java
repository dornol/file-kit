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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // P1
    @Test
    void copyAllParallelAsync_allSucceed() throws Exception {
        when(sync.copy("a", StorageType.S3, "dest")).thenReturn(metadata);
        when(sync.copy("b", StorageType.S3, "dest")).thenReturn(metadata);
        when(sync.copy("c", StorageType.S3, "dest")).thenReturn(metadata);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        BatchTransferResult result = svc.copyAllParallelAsync(
                List.of("a", "b", "c"), StorageType.S3, "dest").get();

        assertEquals(3, result.succeeded().size());
        assertEquals(0, result.failed().size());
    }

    // P2
    @Test
    void copyAllParallelAsync_mixedOutcomes() throws Exception {
        when(sync.copy("ok1", StorageType.S3, "dest")).thenReturn(metadata);
        when(sync.copy("ok2", StorageType.S3, "dest")).thenReturn(metadata);
        when(sync.copy("bad", StorageType.S3, "dest")).thenThrow(
                new FileStorageException(FileStorageException.FILE_NOT_FOUND, "gone"));

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        BatchTransferResult result = svc.copyAllParallelAsync(
                List.of("ok1", "bad", "ok2"), StorageType.S3, "dest").get();

        assertEquals(2, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertEquals("gone", result.failed().get("bad"));
    }

    // P3
    @Test
    void copyAllParallelAsync_emptyInput_emptyResult() throws Exception {
        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        BatchTransferResult result = svc.copyAllParallelAsync(
                List.of(), StorageType.S3, "dest").get();

        assertEquals(0, result.succeeded().size());
        assertTrue(result.allSucceeded());
    }

    // P4
    @Test
    void copyAllParallelAsync_failureMessage_unwrapped() throws Exception {
        when(sync.copy("bad", StorageType.S3, "dest")).thenThrow(
                new IllegalStateException("custom cause"));

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        BatchTransferResult result = svc.copyAllParallelAsync(
                List.of("bad"), StorageType.S3, "dest").get();

        assertEquals("custom cause", result.failed().get("bad"));
    }

    // P5 — move parallel also works
    @Test
    void moveAllParallelAsync_allSucceed() throws Exception {
        when(sync.move(any(), any(), any())).thenReturn(metadata);

        AsyncFileTransferService svc = AsyncFileTransferService.builder(sync).build();

        BatchTransferResult result = svc.moveAllParallelAsync(
                List.of("a", "b"), StorageType.S3, "dest").get();

        assertEquals(2, result.succeeded().size());
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
