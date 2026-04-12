package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileMetadata;

import java.util.List;
import java.util.Map;

/**
 * Result of a batch upload operation.
 *
 * @param succeeded list of successfully uploaded file metadata
 * @param failed    map of original filenames to their failure messages
 */
public record BatchUploadResult(
        List<FileMetadata> succeeded,
        Map<String, String> failed
) {
    public BatchUploadResult {
        succeeded = List.copyOf(succeeded);
        failed = Map.copyOf(failed);
    }

    /**
     * Returns the total number of requested uploads.
     */
    public int totalRequested() {
        return succeeded.size() + failed.size();
    }

    /**
     * Returns {@code true} if all uploads succeeded.
     */
    public boolean allSucceeded() {
        return failed.isEmpty();
    }
}
