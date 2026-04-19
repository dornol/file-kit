package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.upload.BatchUploadResult;
import io.github.dornol.filekit.upload.FileUploadService;
import io.github.dornol.filekit.upload.UploadCallback;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncFileUploadServiceTest {

    enum StorageType { LOCAL }

    private final FileUploadService sync = mock(FileUploadService.class);
    private final FileSource fileSource = mock(FileSource.class);

    private final FileMetadata metadata = new FileMetadata(
            "key", "test.txt", 5, "abc",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL));

    // U1
    @Test
    void uploadAsync_success_returnsMetadata() throws Exception {
        when(sync.upload(fileSource, StorageType.LOCAL, "bucket")).thenReturn(metadata);

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();

        FileMetadata result = svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket").get();

        assertSame(metadata, result);
    }

    // U2
    @Test
    void uploadAsyncWithCallback_passesCallbackThrough() throws Exception {
        UploadCallback cb = mock(UploadCallback.class);
        when(sync.upload(fileSource, StorageType.LOCAL, "bucket", cb)).thenReturn(metadata);

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();

        svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket", cb).get();

        verify(sync).upload(fileSource, StorageType.LOCAL, "bucket", cb);
    }

    // U3
    @Test
    void uploadAsync_ioException_surfacesAsCompletionExceptionCause() throws Exception {
        IOException ioErr = new IOException("disk full");
        when(sync.upload(any(), any(), anyString())).thenThrow(ioErr);

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket").get());

        assertSame(ioErr, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // U4
    @Test
    void uploadAsync_fileStorageException_surfacesAsCause() throws IOException {
        FileStorageException fse = new FileStorageException(
                FileStorageException.FILE_TOO_LARGE, "too big");
        when(sync.upload(any(), any(), anyString())).thenThrow(fse);

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket").get());

        assertSame(fse, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // U5
    @Test
    void uploadAllAsync_returnsBatchResult() throws Exception {
        BatchUploadResult batch = new BatchUploadResult(List.of(metadata), Map.of());
        when(sync.uploadAll(any(), eq(StorageType.LOCAL), eq("bucket"))).thenReturn(batch);

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();

        BatchUploadResult result = svc.uploadAllAsync(
                List.of(fileSource), StorageType.LOCAL, "bucket").get();

        assertSame(batch, result);
    }

    // U6
    @Test
    void builder_nullSync_throws() {
        assertThrows(NullPointerException.class, () -> AsyncFileUploadService.builder(null));
    }

    // U7
    @Test
    void builder_nullExecutor_throws() {
        assertThrows(NullPointerException.class,
                () -> AsyncFileUploadService.builder(sync).executor(null));
    }

    // U8 — default executor is ForkJoinPool.commonPool(). Capture the thread
    // that ran the supplier (inside supplyAsync, not in thenAccept) and verify
    // its name matches the commonPool worker prefix.
    @Test
    void builder_defaultExecutor_commonPool() throws Exception {
        AtomicReference<String> supplierThread = new AtomicReference<>();
        when(sync.upload(any(), any(), anyString())).thenAnswer(inv -> {
            supplierThread.set(Thread.currentThread().getName());
            return metadata;
        });

        AsyncFileUploadService svc = AsyncFileUploadService.builder(sync).build();
        svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket").get();

        String name = supplierThread.get();
        assertNotNull(name);
        assertTrue(name.startsWith("ForkJoinPool.commonPool-worker-"),
                "expected commonPool worker thread, got: " + name);
    }

    // U9
    @Test
    void injectedExecutor_runsOnThatExecutor() throws Exception {
        when(sync.upload(any(), any(), anyString())).thenReturn(metadata);
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "file-kit-async-test");
            t.setDaemon(true);
            return t;
        });
        try {
            AsyncFileUploadService svc = AsyncFileUploadService.builder(sync)
                    .executor(dedicated)
                    .build();
            AtomicReference<String> name = new AtomicReference<>();

            CompletableFuture<FileMetadata> cf = svc.uploadAsync(fileSource, StorageType.LOCAL, "bucket");
            cf.get();
            // The supplier runs on our dedicated executor; capture via whenComplete on
            // the same executor to guarantee the observation.
            cf.whenCompleteAsync((m, ex) -> name.set(Thread.currentThread().getName()), dedicated).get();

            assertEquals("file-kit-async-test", name.get());
        } finally {
            dedicated.shutdownNow();
        }
    }

}
