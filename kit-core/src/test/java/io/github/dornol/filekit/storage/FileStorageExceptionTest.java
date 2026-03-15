package io.github.dornol.filekit.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FileStorageExceptionTest {

    @Test
    void constructorWithMessageKey() {
        FileStorageException ex = new FileStorageException(
                FileStorageException.FILE_NOT_FOUND, "not found");
        assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        assertEquals("not found", ex.getMessage());
    }

    @Test
    void constructorWithCause() {
        RuntimeException cause = new RuntimeException("root");
        FileStorageException ex = new FileStorageException(
                FileStorageException.CALLBACK_FAILED, "callback error", cause);
        assertEquals(FileStorageException.CALLBACK_FAILED, ex.getMessageKey());
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageKeyConstants() {
        assertEquals("file-kit.storage.file-not-found", FileStorageException.FILE_NOT_FOUND);
        assertEquals("file-kit.storage.storage-not-found", FileStorageException.STORAGE_NOT_FOUND);
        assertEquals("file-kit.storage.upload-failed", FileStorageException.UPLOAD_FAILED);
        assertEquals("file-kit.storage.download-failed", FileStorageException.DOWNLOAD_FAILED);
        assertEquals("file-kit.storage.delete-failed", FileStorageException.DELETE_FAILED);
        assertEquals("file-kit.storage.callback-failed", FileStorageException.CALLBACK_FAILED);
        assertEquals("file-kit.storage.file-too-large", FileStorageException.FILE_TOO_LARGE);
        assertEquals("file-kit.storage.invalid-filename", FileStorageException.INVALID_FILENAME);
    }

}
