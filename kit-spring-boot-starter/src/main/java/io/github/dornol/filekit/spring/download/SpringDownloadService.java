package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.io.IoUtils;
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

    /**
     * Creates a builder with the two required dependencies.
     *
     * @param metadataRepository repository for file metadata lookup
     * @param storageResolver    resolver to find storage backends
     * @return a new builder instance
     */
    public static Builder builder(FileMetadataRepository metadataRepository,
                                  FileStorageResolver storageResolver) {
        return new Builder(metadataRepository, storageResolver);
    }

    private SpringDownloadService(Builder b) {
        this.metadataRepository = Objects.requireNonNull(b.metadataRepository, "metadataRepository");
        this.storageResolver = Objects.requireNonNull(b.storageResolver, "storageResolver");
        this.fileEncryptor = Objects.requireNonNull(b.fileEncryptor, "fileEncryptor");
    }

    /** @deprecated Use {@link #builder(FileMetadataRepository, FileStorageResolver)} instead. */
    @Deprecated(forRemoval = true)
    public SpringDownloadService(FileMetadataRepository metadataRepository,
                                 FileStorageResolver storageResolver) {
        this(metadataRepository, storageResolver, new NoOpFileEncryptor());
    }

    /** @deprecated Use {@link #builder(FileMetadataRepository, FileStorageResolver)} instead. */
    @Deprecated(forRemoval = true)
    public SpringDownloadService(FileMetadataRepository metadataRepository,
                                 FileStorageResolver storageResolver,
                                 FileEncryptor fileEncryptor) {
        this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        this.fileEncryptor = Objects.requireNonNull(fileEncryptor, "fileEncryptor");
    }

    public static final class Builder {

        private final FileMetadataRepository metadataRepository;
        private final FileStorageResolver storageResolver;
        private FileEncryptor fileEncryptor = new NoOpFileEncryptor();

        private Builder(FileMetadataRepository metadataRepository,
                        FileStorageResolver storageResolver) {
            this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
            this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
        }

        /** @param fileEncryptor encryptor for at-rest decryption */
        public Builder fileEncryptor(FileEncryptor fileEncryptor) {
            this.fileEncryptor = fileEncryptor;
            return this;
        }

        public SpringDownloadService build() {
            return new SpringDownloadService(this);
        }
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

        try {
            return new InputStreamResource(decryptToStream(encrypted));
        } catch (Exception e) {
            IoUtils.closeQuietly(encrypted);
            throw e;
        }
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
        return io.github.dornol.filekit.io.DecryptionHelper.decryptToStream(encryptedContent, fileEncryptor);
    }

}
