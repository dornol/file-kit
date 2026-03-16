package io.github.dornol.filekit.test;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryMetadataRepositoryTest {

    enum StorageType { LOCAL }

    InMemoryMetadataRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMetadataRepository();
    }

    private FileMetadata metadata(String key, String checksum) {
        return new FileMetadata(key, "file.txt", 100, checksum,
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", key + ".txt", StorageType.LOCAL));
    }

    @Test
    void save_andFindByKey() {
        FileMetadata meta = metadata("key1", "checksum1");
        repository.save(meta);

        assertNotNull(repository.findByKey("key1"));
        assertEquals("key1", repository.findByKey("key1").key());
    }

    @Test
    void save_andFindByChecksum() {
        FileMetadata meta = metadata("key1", "checksum1");
        repository.save(meta);

        assertNotNull(repository.findByChecksum("checksum1"));
        assertEquals("key1", repository.findByChecksum("checksum1").key());
    }

    @Test
    void findByKey_returnsNullWhenNotFound() {
        assertNull(repository.findByKey("missing"));
    }

    @Test
    void findByChecksum_returnsNullWhenNotFound() {
        assertNull(repository.findByChecksum("missing"));
    }

    @Test
    void deleteByKey_removesBothKeyAndChecksum() {
        repository.save(metadata("key1", "checksum1"));
        assertEquals(1, repository.count());

        repository.deleteByKey("key1");

        assertEquals(0, repository.count());
        assertNull(repository.findByKey("key1"));
        assertNull(repository.findByChecksum("checksum1"));
    }

    @Test
    void deleteByKey_nonExistent_doesNotThrow() {
        repository.deleteByKey("missing");
        assertEquals(0, repository.count());
    }

    @Test
    void count_reflectsOperations() {
        assertEquals(0, repository.count());

        repository.save(metadata("key1", "c1"));
        assertEquals(1, repository.count());

        repository.save(metadata("key2", "c2"));
        assertEquals(2, repository.count());

        repository.deleteByKey("key1");
        assertEquals(1, repository.count());
    }

    @Test
    void save_overwritesExistingKey() {
        repository.save(metadata("key1", "old-checksum"));
        repository.save(metadata("key1", "new-checksum"));

        assertEquals(1, repository.count());
        assertEquals("new-checksum", repository.findByKey("key1").checksum());
    }

    @Test
    void multipleFiles_independentOperations() {
        repository.save(metadata("a", "ca"));
        repository.save(metadata("b", "cb"));
        repository.save(metadata("c", "cc"));

        repository.deleteByKey("b");

        assertEquals(2, repository.count());
        assertNotNull(repository.findByKey("a"));
        assertNull(repository.findByKey("b"));
        assertNotNull(repository.findByKey("c"));
    }
}
