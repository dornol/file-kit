package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileUploadCommand;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;

    public S3FileStorage(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public Enum<?> getStorageType() {
        return StorageType.S3;
    }

    @Override
    public FileLocation upload(FileUploadCommand command) {
        String objectKey = command.key() + "." + command.extension();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(command.bucket())
                            .key(objectKey)
                            .contentType(command.mimeType())
                            .build(),
                    RequestBody.fromBytes(command.content()));
            return new FileLocation(command.bucket(), objectKey, StorageType.S3);
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "S3 upload failed: " + objectKey, e);
        }
    }

    @Override
    public void delete(FileMetadata metadata) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.DELETE_FAILED,
                    "S3 delete failed: " + metadata.location().objectKey(), e);
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "S3 download failed: " + metadata.location().objectKey(), e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "/files/" + metadata.key() + "/download";
    }

}
