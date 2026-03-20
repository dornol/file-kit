package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageResolverTest {

    enum TestStorageType { LOCAL, S3 }

    @Test
    void resolve_returnsMatchingStorage() {
        FileStorage localStorage = stubStorage(TestStorageType.LOCAL);
        FileStorage s3Storage = stubStorage(TestStorageType.S3);
        FileStorageResolver resolver = new FileStorageResolver(List.of(localStorage, s3Storage));

        FileStorage result = resolver.resolve(TestStorageType.LOCAL);
        assertNotNull(result);
        assertEquals(TestStorageType.LOCAL, result.getStorageType());
    }

    @Test
    void resolve_throwsForUnregisteredType() {
        FileStorage localStorage = stubStorage(TestStorageType.LOCAL);
        FileStorageResolver resolver = new FileStorageResolver(List.of(localStorage));

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> resolver.resolve(TestStorageType.S3));
        assertNotNull(ex.getMessage());
    }

    @Test
    void resolve_worksWithSingleStorage() {
        FileStorage storage = stubStorage(TestStorageType.S3);
        FileStorageResolver resolver = new FileStorageResolver(List.of(storage));

        assertEquals(storage, resolver.resolve(TestStorageType.S3));
    }

    @Test
    void duplicateStorageType_throws() {
        FileStorage first = stubStorage(TestStorageType.LOCAL);
        FileStorage second = stubStorage(TestStorageType.LOCAL);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new FileStorageResolver(List.of(first, second)));
        assertTrue(ex.getMessage().contains("Duplicate storageType"));
        assertTrue(ex.getMessage().contains("LOCAL"));
    }

    private FileStorage stubStorage(Enum<?> type) {
        return new FileStorage() {
            @Override
            public Enum<?> getStorageType() {
                return type;
            }

            @Override
            public FileLocation upload(FileUploadCommand command) {
                return null;
            }

            @Override
            public void delete(FileMetadata metadata) {}

            @Override
            public InputStream load(FileMetadata metadata) {
                return InputStream.nullInputStream();
            }

            @Override
            public String resolveUri(FileMetadata metadata) {
                return "";
            }
        };
    }

}
