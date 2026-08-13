package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalOrphanCleanupServiceTest {

    @Test
    void deletesOnlyOldUnreferencedFiles(@TempDir Path root) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root, StorageType.LOCAL);
        FileMetadataRepositoryAdapter repository = mock(FileMetadataRepositoryAdapter.class);
        FileMetadata referenced = new FileMetadata(
                "key", "file.txt", 4, "checksum",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("uploads", "referenced.txt", StorageType.LOCAL));
        when(repository.findAll()).thenReturn(List.of(referenced));

        Path referencedPath = root.resolve("uploads/referenced.txt");
        Path oldOrphan = root.resolve("uploads/old-orphan.txt");
        Path newOrphan = root.resolve("uploads/new-orphan.txt");
        Files.createDirectories(referencedPath.getParent());
        Files.writeString(referencedPath, "keep");
        Files.writeString(oldOrphan, "delete");
        Files.writeString(newOrphan, "keep for now");
        Files.setLastModifiedTime(oldOrphan, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        LocalOrphanCleanupService service = new LocalOrphanCleanupService(
                repository, storage, "1h");

        LocalOrphanCleanupService.CleanupResult result = service.cleanup();

        assertThat(result.examined()).isEqualTo(3);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(referencedPath).exists();
        assertThat(oldOrphan).doesNotExist();
        assertThat(newOrphan).exists();
    }
}
