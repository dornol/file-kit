package io.github.dornol.filekit.metadata;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.spi.FileEventListener;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRenameServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileEventListener listener = mock(FileEventListener.class);
    FileRenameService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "old-name.txt", 100, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = FileRenameService.builder(metadataRepository)
                .eventPublisher(new FileEventPublisher(List.of(listener)))
                .build();
    }

    @Nested
    class Rename {

        @Test
        void renamesSuccessfully() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(metadataRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = service.rename("file-key", "new-name.txt");

            assertEquals("file-key", result.key());
            assertEquals("new-name.txt", result.name());
            assertEquals(metadata.size(), result.size());
            assertEquals(metadata.checksum(), result.checksum());
        }

        @Test
        void firesRenamedEvent() {
            when(metadataRepository.getByKey("file-key")).thenReturn(metadata);
            when(metadataRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

            FileMetadata result = service.rename("file-key", "new-name.txt");

            verify(listener).onRenamed(metadata, result);
        }

        @Test
        void fileNotFound_throws() {
            when(metadataRepository.getByKey("missing")).thenThrow(
                    new FileStorageException(FileStorageException.FILE_NOT_FOUND, "not found"));

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> service.rename("missing", "new.txt"));
            assertEquals(FileStorageException.FILE_NOT_FOUND, ex.getMessageKey());
        }
    }

    @Nested
    class Validation {

        @Test
        void nullFileKey_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.rename(null, "name.txt"));
        }

        @Test
        void nullNewName_throws() {
            assertThrows(NullPointerException.class,
                    () -> service.rename("file-key", null));
        }

        @Test
        void tooLongFilename_throws() {
            String longName = "a".repeat(201) + ".txt";

            assertThrows(FileStorageException.class,
                    () -> service.rename("file-key", longName));
            verify(metadataRepository, never()).update(any());
        }

        @Test
        void pathTraversal_throws() {
            assertThrows(FileStorageException.class,
                    () -> service.rename("file-key", "../etc/passwd"));
            verify(metadataRepository, never()).update(any());
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void nullMetadataRepository_throws() {
            assertThrows(NullPointerException.class,
                    () -> FileRenameService.builder(null));
        }

        @Test
        void defaultsWork() {
            FileRenameService svc = FileRenameService.builder(metadataRepository).build();
            assertNotNull(svc);
        }
    }
}
