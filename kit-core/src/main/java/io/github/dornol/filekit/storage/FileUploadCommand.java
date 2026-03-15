package io.github.dornol.filekit.storage;

import java.util.Objects;

/**
 * Command object passed to {@link FileStorage#upload(FileUploadCommand)}.
 *
 * @param key              unique key for the file
 * @param originalFilename original filename from the client
 * @param content          raw file bytes
 * @param mimeType         detected MIME type
 * @param extension        file extension without dot
 * @param bucket           target storage bucket (alphanumeric, dot, hyphen, underscore only)
 */
public record FileUploadCommand(
        String key,
        String originalFilename,
        byte[] content,
        String mimeType,
        String extension,
        String bucket
) {
    public FileUploadCommand {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(bucket, "bucket");
        if (!bucket.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucket);
        }
    }
}
