package io.github.dornol.filekit.delete;

import java.util.List;
import java.util.Map;

/**
 * Result of a batch delete operation.
 *
 * @param succeeded list of successfully deleted file keys
 * @param failed    map of file keys to their failure exceptions
 */
public record BatchDeleteResult(
        List<String> succeeded,
        Map<String, String> failed
) {
    public BatchDeleteResult {
        succeeded = List.copyOf(succeeded);
        failed = Map.copyOf(failed);
    }

    /**
     * Returns the total number of requested deletions.
     */
    public int totalRequested() {
        return succeeded.size() + failed.size();
    }

    /**
     * Returns {@code true} if all deletions succeeded.
     */
    public boolean allSucceeded() {
        return failed.isEmpty();
    }
}
