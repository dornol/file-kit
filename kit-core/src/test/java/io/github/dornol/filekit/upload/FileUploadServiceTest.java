package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    enum StorageType { LOCAL }

    ChecksumCalculator checksumCalculator = mock(ChecksumCalculator.class);
    FileMetadataRepository metadataRepository = mock(FileMetadataRepository.class);
    FileFormatExtractor formatExtractor = mock(FileFormatExtractor.class);
    FileStorageResolver storageResolver = mock(FileStorageResolver.class);
    FileStorage fileStorage = mock(FileStorage.class);
    FileSource fileSource = mock(FileSource.class);

    FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService(checksumCalculator, metadataRepository,
                formatExtractor, storageResolver);
    }

    @Test
    void upload_fullFlow() throws IOException {
        byte[] content = "hello".getBytes();
        FileFormat format = new FileFormat("text/plain", "txt", "text");
        FileLocation location = new FileLocation("my-bucket", "some-key", StorageType.LOCAL);

        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(fileSource.getOriginalFilename()).thenReturn("test.txt");
        when(checksumCalculator.checksum(content)).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
        when(formatExtractor.extract(any())).thenReturn(format);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.upload(any())).thenReturn(location);
        when(metadataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "my-bucket");

        assertNotNull(result);
        assertEquals("test.txt", result.name());
        assertEquals(content.length, result.size());
        assertEquals("abc123", result.checksum());
        assertEquals(format, result.format());
        assertEquals(location, result.location());

        ArgumentCaptor<FileUploadCommand> cmdCaptor = ArgumentCaptor.forClass(FileUploadCommand.class);
        verify(fileStorage).upload(cmdCaptor.capture());
        FileUploadCommand cmd = cmdCaptor.getValue();
        assertEquals("test.txt", cmd.originalFilename());
        assertEquals("text/plain", cmd.mimeType());
        assertEquals("txt", cmd.extension());
        assertEquals("my-bucket", cmd.bucket());
    }

    @Test
    void upload_duplicateChecksum_returnsExisting() throws IOException {
        byte[] content = "hello".getBytes();
        FileMetadata existing = new FileMetadata("existing-key", "test.txt", 5, "abc123",
                new FileFormat("text/plain", "txt", "text"),
                new FileLocation("bucket", "key", StorageType.LOCAL));

        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(checksumCalculator.checksum(content)).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(existing);

        FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "my-bucket");

        assertEquals(existing, result);
        verify(formatExtractor, never()).extract(any());
        verify(storageResolver, never()).resolve(any());
        verify(metadataRepository, never()).save(any());
    }

    @Test
    void upload_withCallback_runsBeforeSave() throws Exception {
        byte[] content = "hello".getBytes();
        FileFormat format = new FileFormat("text/plain", "txt", "text");
        FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(fileSource.getOriginalFilename()).thenReturn("test.txt");
        when(checksumCalculator.checksum(content)).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
        when(formatExtractor.extract(any())).thenReturn(format);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.upload(any())).thenReturn(location);
        when(metadataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UploadCallback callback = mock(UploadCallback.class);
        FileMetadata result = service.upload(fileSource, StorageType.LOCAL, "bucket", callback);

        assertNotNull(result);
        verify(callback).onUploaded(any());
        verify(metadataRepository).save(any());
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void upload_withCallback_deletesFileOnFailure() throws Exception {
        byte[] content = "hello".getBytes();
        FileFormat format = new FileFormat("text/plain", "txt", "text");
        FileLocation location = new FileLocation("bucket", "key", StorageType.LOCAL);

        when(fileSource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(fileSource.getOriginalFilename()).thenReturn("test.txt");
        when(checksumCalculator.checksum(content)).thenReturn("abc123");
        when(metadataRepository.findByChecksum("abc123")).thenReturn(null);
        when(formatExtractor.extract(any())).thenReturn(format);
        when(storageResolver.resolve(StorageType.LOCAL)).thenReturn(fileStorage);
        when(fileStorage.upload(any())).thenReturn(location);

        UploadCallback callback = mock(UploadCallback.class);
        doThrow(new RuntimeException("business error")).when(callback).onUploaded(any());

        assertThrows(FileStorageException.class,
                () -> service.upload(fileSource, StorageType.LOCAL, "bucket", callback));

        verify(fileStorage).delete(any());
        verify(metadataRepository, never()).save(any());
    }

}
