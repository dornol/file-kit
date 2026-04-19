package io.github.dornol.filekit.delete;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
