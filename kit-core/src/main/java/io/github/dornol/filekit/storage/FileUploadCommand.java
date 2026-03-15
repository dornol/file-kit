package io.github.dornol.filekit.storage;

/**
 * Command object passed to {@link FileStorage#upload(FileUploadCommand)}.
 *
 * @param key              unique key for the file
 * @param originalFilename original filename from the client
 * @param content          raw file bytes
 * @param mimeType         detected MIME type
 * @param extension        file extension without dot
 * @param bucket           target storage bucket
 */
public record FileUploadCommand(
        String key,
        String originalFilename,
        byte[] content,
        String mimeType,
        String extension,
        String bucket
) {
}
