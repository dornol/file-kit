package io.github.dornol.filekit.async;

import java.util.concurrent.CompletionException;

/**
 * Package-private helpers shared by the parallel batch methods.
 */
final class AsyncInternal {

    private AsyncInternal() {}

    /**
     * Unwraps a {@link CompletionException} to get the underlying cause and
     * returns its message, falling back to the simple class name when the
     * message is null. Used to keep parallel-batch failure messages aligned
     * with the sync batch failure format.
     */
    static String unwrapMessage(Throwable t) {
        Throwable cause = (t instanceof CompletionException && t.getCause() != null)
                ? t.getCause()
                : t;
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }
}
