package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.spring.storage.SpringFileStorage;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileStorageResolver;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Spring-aware download service that returns files as Spring {@link Resource} objects.
 *
 * <p>Delegates to {@link SpringFileStorage#loadResource(FileMetadata)} when the
 * storage implements {@link SpringFileStorage}, otherwise wraps the stream
 * in an {@link InputStreamResource}.</p>
 */
public class SpringDownloadService {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageResolver storageResolver;
    private final FileEncryptor fileEncryptor;

    /** Creates a download service without encryption. */
    public SpringDownloadService(FileMetadataRepository metadataRepository,
                                 FileStorageResolver storageResolver) {
        this(metadataRepository, storageResolver, new NoOpFileEncryptor());
    }

    /**
     * Creates a download service with the specified encryptor.
     *
     * @param metadataRepository repository for file metadata lookup
     * @param storageResolver    resolver to find storage backends
     * @param fileEncryptor      encryptor for at-rest decryption
     */
    public SpringDownloadService(FileMetadataRepository metadataRepository,
                                 FileStorageResolver storageResolver,
                                 FileEncryptor fileEncryptor) {
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        this.fileEncryptor = Objects.requireNonNull(fileEncryptor, "fileEncryptor");
    }

    public Resource loadResource(String fileKey) {
        FileMetadata metadata = metadataRepository.getByKey(fileKey);
        FileStorage storage = storageResolver.resolve(metadata.location().storageType());

        if (!fileEncryptor.isEnabled()) {
            if (storage instanceof SpringFileStorage springStorage) {
                return springStorage.loadResource(metadata);
            }
            return new InputStreamResource(storage.load(metadata));
        }

        // When encryption is active, always decrypt through stream
        InputStream encrypted = (storage instanceof SpringFileStorage springStorage)
                ? getInputStream(springStorage.loadResource(metadata))
                : storage.load(metadata);

        return new InputStreamResource(decryptToStream(encrypted));
    }

    private static InputStream getInputStream(Resource resource) {
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "Failed to read resource stream", e);
        }
    }

    private InputStream decryptToStream(InputStream encryptedContent) {
        Path decryptedFile = null;
        try {
            decryptedFile = Files.createTempFile("file-kit-decrypted-", ".tmp");
            try (InputStream in = encryptedContent;
                 OutputStream out = Files.newOutputStream(decryptedFile)) {
                fileEncryptor.decrypt(in, out);
            }
            // Return an InputStream that deletes temp file on close
            return new DeleteOnCloseInputStream(decryptedFile);
        } catch (IOException e) {
            if (decryptedFile != null) {
                try {
                    Files.deleteIfExists(decryptedFile);
                } catch (IOException deleteEx) {
                    e.addSuppressed(deleteEx);
                }
            }
            throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                    "Failed to decrypt file content", e);
        }
    }

    private static class DeleteOnCloseInputStream extends InputStream {

        private final InputStream delegate;
        private final Path tempFile;

        DeleteOnCloseInputStream(Path tempFile) throws IOException {
            this.tempFile = tempFile;
            this.delegate = Files.newInputStream(tempFile);
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

}
