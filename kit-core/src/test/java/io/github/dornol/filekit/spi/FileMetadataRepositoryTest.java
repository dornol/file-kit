package io.github.dornol.filekit.spi;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMetadataRepositoryTest {

    enum StorageType { LOCAL }

    private final FileMetadata sample = new FileMetadata(
            "key-1", "test.txt", 100, "abc123",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "key-1.txt", StorageType.LOCAL)
    );

    @Test
    void getByKey_returnsMetadata_whenFound() {
        FileMetadataRepository repo = stubRepository(sample);

        FileMetadata result = repo.getByKey("key-1");

        assertSame(sample, result);
    }

    @Test
    void getByKey_throwsFileStorageException_whenNotFound() {
        FileMetadataRepository repo = stubRepository(null);

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> repo.getByKey("missing"));

        assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
    }

    @Test
    void getByKey_exceptionMessage_containsKey() {
        FileMetadataRepository repo = stubRepository(null);

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> repo.getByKey("my-file-key"));

        assertTrue(ex.getMessage().contains("my-file-key"));
    }

    @Test
    void existsByKey_returnsTrue_whenFound() {
        FileMetadataRepository repo = stubRepository(sample);

        assertTrue(repo.existsByKey("key-1"));
    }

    @Test
    void existsByKey_returnsFalse_whenNotFound() {
        FileMetadataRepository repo = stubRepository(null);

        assertFalse(repo.existsByKey("missing"));
    }

    @Test
    void update_defaultDelegatesToSave() {
        FileMetadataRepository repo = stubRepository(null);

        FileMetadata result = repo.update(sample);

        assertSame(sample, result);
    }

    private static FileMetadataRepository stubRepository(FileMetadata returnValue) {
        return new FileMetadataRepository() {
            @Override
            public FileMetadata findByChecksum(String checksum) {
                return null;
            }

            @Override
            public FileMetadata findByKey(String key) {
                return returnValue;
            }

            @Override
            public FileMetadata save(FileMetadata metadata) {
                return metadata;
            }

            @Override
            public void deleteByKey(String key) {
                // no-op for stub
            }
        };
    }

}
