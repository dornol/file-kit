package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileUploadCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path baseDir;

    public LocalFileStorage() {
        this.baseDir = Path.of(System.getProperty("java.io.tmpdir"), "file-kit-example");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("LocalFileStorage initialized at: {}", baseDir);
    }

    @Override
    public Enum<?> getStorageType() {
        return StorageType.LOCAL;
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        Path bucketDir = baseDir.resolve(command.bucket());
        try {
            Files.createDirectories(bucketDir);
            String objectKey = command.key() + "." + command.extension();
            Path target = bucketDir.resolve(objectKey);
            Files.write(target, command.content());
            log.info("Uploaded file: {}", target);
            return new FileLocation(command.bucket(), objectKey, StorageType.LOCAL);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        Path filePath = baseDir
                .resolve(metadata.location().bucket())
                .resolve(metadata.location().objectKey());
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "/files/" + metadata.key() + "/download";
    }

}
