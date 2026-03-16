package io.github.dornol.filekit.storage;

import io.github.dornol.filekit.validator.BucketNameValidator;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * Command object passed to {@link FileStorage#upload(FileUploadCommand)}.
 *
 * @param key              unique key for the file
 * @param originalFilename original filename from the client
 * @param content          file content as an input stream
 * @param contentLength    size of the content in bytes
 * @param mimeType         detected MIME type
 * @param extension        file extension without dot
 * @param bucket           target storage bucket (alphanumeric, dot, hyphen, underscore only)
 */
public record FileUploadCommand(
        String key,
        @Nullable String originalFilename,
        InputStream content,
        long contentLength,
        String mimeType,
        String extension,
        String bucket
) {
    public FileUploadCommand {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(bucket, "bucket");
        BucketNameValidator.validate(bucket);
    }

    /**
     * Creates a command from raw bytes (convenience factory for backward compatibility).
     */
    public static FileUploadCommand ofBytes(String key, String originalFilename, byte[] bytes,
                                             String mimeType, String extension, String bucket) {
        return new FileUploadCommand(key, originalFilename,
                new ByteArrayInputStream(bytes), bytes.length, mimeType, extension, bucket);
    }
}
