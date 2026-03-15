package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spring.storage.SpringFileStorage;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringDownloadServiceTest {

    enum StorageType { LOCAL }

    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);

    SpringDownloadService service;

    private final FileMetadata metadata = new FileMetadata(
            "file-key", "test.txt", 5, "checksum",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "obj-key", StorageType.LOCAL)
    );

    @BeforeEach
    void setUp() {
        service = new SpringDownloadService(metadataRepository, storageResolver);
    }

    @Test
    void loadResource_delegatesToSpringFileStorage() {
        Resource expected = mock(Resource.class);
        SpringFileStorage springStorage = mock(SpringFileStorage.class);

        when(metadataRepository.findByKey("file-key")).thenReturn(metadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(springStorage);
        when(springStorage.loadResource(metadata)).thenReturn(expected);

        Resource result = service.loadResource("file-key");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void loadResource_wrapsInputStreamForPlainFileStorage() {
        InputStream content = new ByteArrayInputStream("hello".getBytes());
        FileStorage plainStorage = mock(FileStorage.class);

        when(metadataRepository.findByKey("file-key")).thenReturn(metadata);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(plainStorage);
        when(plainStorage.load(metadata)).thenReturn(content);

        Resource result = service.loadResource("file-key");

        assertThat(result).isInstanceOf(InputStreamResource.class);
    }

    @Test
    void loadResource_throwsWhenFileNotFound() {
        when(metadataRepository.findByKey("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.loadResource("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
