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
