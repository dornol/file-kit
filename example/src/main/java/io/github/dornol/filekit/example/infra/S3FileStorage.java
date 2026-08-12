package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageException;
import io.github.dornol.filekit.storage.FileUploadCommand;
import io.github.dornol.filekit.storage.StorageHealthCheck;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

public class S3FileStorage implements FileStorage, StorageHealthCheck {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FileStorage(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.s3Presigner = Objects.requireNonNull(s3Presigner, "s3Presigner");
    }

    @Override
    public Enum<?> getStorageType() {
        return StorageType.S3;
    }

    @Override
    public void check() {
        try {
            s3Client.listBuckets();
        } catch (RuntimeException e) {
            throw new IllegalStateException("S3 backend is unavailable", e);
        }
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
                            .contentLength(command.contentLength())
                            .build(),
                    RequestBody.fromInputStream(command.content(), command.contentLength()));
            return new FileLocation(command.bucket(), objectKey, StorageType.S3);
        } catch (RuntimeException e) {
            throw new FileStorageException(FileStorageException.UPLOAD_FAILED,
                    "S3 upload failed: bucket=" + command.bucket() + ", key=" + objectKey, e);
        }
    }

    @Override
    public void delete(FileMetadata metadata) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (RuntimeException e) {
            throw new FileStorageException(FileStorageException.DELETE_FAILED,
                    "S3 delete failed: bucket=" + metadata.location().bucket()
                            + ", key=" + metadata.location().objectKey(), e);
        }
    }

    @Override
    public InputStream load(FileMetadata metadata) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build());
        } catch (RuntimeException e) {
            throw new FileStorageException(FileStorageException.DOWNLOAD_FAILED,
                    "S3 download failed: bucket=" + metadata.location().bucket()
                            + ", key=" + metadata.location().objectKey(), e);
        }
    }

    @Override
    public String resolveUri(FileMetadata metadata) {
        return "/files/" + metadata.key() + "/download";
    }

    @Override
    public String generatePresignedUrl(FileMetadata metadata, Duration expiration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(metadata.location().bucket())
                    .key(metadata.location().objectKey())
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (RuntimeException e) {
            throw new FileStorageException(FileStorageException.PRESIGNED_URL_FAILED,
                    "Failed to generate pre-signed URL for: bucket=" + metadata.location().bucket()
                            + ", key=" + metadata.location().objectKey(), e);
        }
    }

}
