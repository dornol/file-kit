package io.github.dornol.filekit.transfer;

import io.github.dornol.filekit.domain.FileMetadata;

import java.util.List;
import java.util.Map;

/**
 * Result of a batch copy or move operation.
 *
 * @param succeeded list of successfully transferred file metadata (the new copies/moves)
 * @param failed    map of source file keys to their failure messages
 */
public record BatchTransferResult(
        List<FileMetadata> succeeded,
        Map<String, String> failed
) {
    public BatchTransferResult {
        succeeded = List.copyOf(succeeded);
        failed = Map.copyOf(failed);
    }

    /**
     * Returns the total number of requested transfers.
     */
    public int totalRequested() {
        return succeeded.size() + failed.size();
    }

    /**
     * Returns {@code true} if all transfers succeeded.
     */
    public boolean allSucceeded() {
        return failed.isEmpty();
    }
}
