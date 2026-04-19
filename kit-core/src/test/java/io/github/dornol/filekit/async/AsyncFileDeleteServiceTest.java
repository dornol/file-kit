package io.github.dornol.filekit.async;

import io.github.dornol.filekit.delete.BatchDeleteResult;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncFileDeleteServiceTest {

    private final FileDeleteService sync = mock(FileDeleteService.class);

    // D1
    @Test
    void deleteAsync_success_completesWithVoid() throws Exception {
        doNothing().when(sync).delete("key");

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        assertNull(svc.deleteAsync("key").get());
        verify(sync).delete("key");
    }

    // D2
    @Test
    void deleteAllAsync_success() throws Exception {
        BatchDeleteResult batch = new BatchDeleteResult(List.of("k1"), Map.of());
        when(sync.deleteAll(any())).thenReturn(batch);

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        assertSame(batch, svc.deleteAllAsync(List.of("k1")).get());
    }

    // D3
    @Test
    void deleteAsync_exceptionPropagates() {
        FileStorageException fse = new FileStorageException(
                FileStorageException.FILE_NOT_FOUND, "missing");
        doThrow(fse).when(sync).delete("key");

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.deleteAsync("key").get());
        assertSame(fse, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // D4
    @Test
    void builder_nullSync_throws() {
        assertThrows(NullPointerException.class, () -> AsyncFileDeleteService.builder(null));
    }

    @Test
    void builder_nullExecutor_throws() {
        assertThrows(NullPointerException.class,
                () -> AsyncFileDeleteService.builder(sync).executor(null));
    }

    // P1
    @Test
    void deleteAllParallelAsync_allSucceed() throws Exception {
        doNothing().when(sync).delete("a");
        doNothing().when(sync).delete("b");
        doNothing().when(sync).delete("c");

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        BatchDeleteResult result = svc.deleteAllParallelAsync(List.of("a", "b", "c")).get();

        assertEquals(3, result.succeeded().size());
        assertEquals(0, result.failed().size());
    }

    // P2
    @Test
    void deleteAllParallelAsync_mixedOutcomes() throws Exception {
        doNothing().when(sync).delete("ok1");
        doNothing().when(sync).delete("ok2");
        doThrow(new FileStorageException(FileStorageException.FILE_NOT_FOUND, "gone"))
                .when(sync).delete("bad");

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        BatchDeleteResult result = svc.deleteAllParallelAsync(List.of("ok1", "bad", "ok2")).get();

        assertEquals(2, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertEquals("gone", result.failed().get("bad"));
    }

    // P3
    @Test
    void deleteAllParallelAsync_emptyInput() throws Exception {
        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        BatchDeleteResult result = svc.deleteAllParallelAsync(List.of()).get();

        assertEquals(0, result.succeeded().size());
    }

    // P4
    @Test
    void deleteAllParallelAsync_failureMessageUnwrapped() throws Exception {
        doThrow(new IllegalStateException("conn refused")).when(sync).delete("bad");

        AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync).build();

        BatchDeleteResult result = svc.deleteAllParallelAsync(List.of("bad")).get();

        assertEquals("conn refused", result.failed().get("bad"));
    }

    // D5
    @Test
    void injectedExecutor_runsOnThatExecutor() throws Exception {
        doNothing().when(sync).delete("key");
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "delete-test-thread");
            t.setDaemon(true);
            return t;
        });
        try {
            AsyncFileDeleteService svc = AsyncFileDeleteService.builder(sync)
                    .executor(dedicated).build();
            AtomicReference<String> name = new AtomicReference<>();

            CompletableFuture<Void> cf = svc.deleteAsync("key");
            cf.get();
            cf.whenCompleteAsync((v, ex) -> name.set(Thread.currentThread().getName()), dedicated).get();

            assertEquals("delete-test-thread", name.get());
        } finally {
            dedicated.shutdownNow();
        }
    }
}
