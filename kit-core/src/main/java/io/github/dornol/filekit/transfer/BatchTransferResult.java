package io.github.dornol.filekit.transfer;

import io.github.dornol.filekit.domain.FileMetadata;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Aggregates failure reasons from {@link #failed} into a count by message.
     *
     * <p>Useful when many files fail for the same underlying reason (e.g. a
     * storage outage): the per-file map may contain dozens of identical
     * entries, whereas this returns {@code {"reason" → count}}.</p>
     *
     * @return immutable map of failure reason → count; empty when all succeeded
     * @since 0.1.19
     */
    public Map<String, Integer> failureReasons() {
        return failed.values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        reason -> 1,
                        Integer::sum));
    }
}
