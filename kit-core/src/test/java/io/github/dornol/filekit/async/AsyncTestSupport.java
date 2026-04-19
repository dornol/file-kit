package io.github.dornol.filekit.async;

import java.util.concurrent.CompletionException;

/**
 * Shared helpers for async-adapter tests.
 */
final class AsyncTestSupport {

    private AsyncTestSupport() {}

    /**
     * Unwraps chained {@link CompletionException}s down to the underlying cause.
     * Used when the sync service throws an exception and the test wants to
     * assert on the original type.
     */
    static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current instanceof CompletionException ce && ce.getCause() != null) {
            current = ce.getCause();
        }
        return current;
    }
}
