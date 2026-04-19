package io.github.dornol.filekit.async;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.metadata.FileRenameService;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

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

class AsyncFileRenameServiceTest {

    enum StorageType { LOCAL }

    private final FileRenameService sync = mock(FileRenameService.class);
    private final FileMetadata metadata = new FileMetadata(
            "key", "renamed.txt", 5, "abc",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj", StorageType.LOCAL));

    // R1
    @Test
    void renameAsync_success() throws Exception {
        when(sync.rename("key", "renamed.txt")).thenReturn(metadata);

        AsyncFileRenameService svc = AsyncFileRenameService.builder(sync).build();

        assertSame(metadata, svc.renameAsync("key", "renamed.txt").get());
    }

    // R2
    @Test
    void renameAsync_exceptionPropagates() {
        FileStorageException fse = new FileStorageException(
                FileStorageException.INVALID_FILENAME, "bad name");
        when(sync.rename("key", "bad/name")).thenThrow(fse);

        AsyncFileRenameService svc = AsyncFileRenameService.builder(sync).build();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> svc.renameAsync("key", "bad/name").get());
        assertSame(fse, AsyncTestSupport.unwrap(ex.getCause()));
    }

    // R3
    @Test
    void builder_nullSync_throws() {
        assertThrows(NullPointerException.class, () -> AsyncFileRenameService.builder(null));
    }

    @Test
    void builder_nullExecutor_throws() {
        assertThrows(NullPointerException.class,
                () -> AsyncFileRenameService.builder(sync).executor(null));
    }

    // R4
    @Test
    void injectedExecutor_runsOnThatExecutor() throws Exception {
        when(sync.rename("k", "new")).thenReturn(metadata);
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rename-test-thread");
            t.setDaemon(true);
            return t;
        });
        try {
            AsyncFileRenameService svc = AsyncFileRenameService.builder(sync)
                    .executor(dedicated).build();
            AtomicReference<String> name = new AtomicReference<>();

            CompletableFuture<FileMetadata> cf = svc.renameAsync("k", "new");
            cf.get();
            cf.whenCompleteAsync((m, ex) -> name.set(Thread.currentThread().getName()), dedicated).get();

            assertEquals("rename-test-thread", name.get());
        } finally {
            dedicated.shutdownNow();
        }
    }
}
