package io.github.dornol.filekit.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fallback {@link ChecksumComputation} that buffers all updates in memory and
 * delegates to {@link ChecksumCalculator#checksum(byte[])} on {@link #finish()}.
 *
 * <p><b>Memory:</b> buffers the full input in a {@link ByteArrayOutputStream}
 * with no size cap. Downloading or hashing arbitrarily large files through a
 * {@link ChecksumCalculator} that relies on this fallback can exhaust the JVM
 * heap. Custom calculators should override
 * {@link ChecksumCalculator#newComputation()} with a streaming implementation
 * whenever the input is not known to be small.</p>
 *
 * <p>A single WARN is logged on first use per class load to surface this
 * fallback path to operators.</p>
 */
final class BufferingComputation implements ChecksumComputation {

    private static final Logger log = LoggerFactory.getLogger(BufferingComputation.class);
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private final ChecksumCalculator delegate;
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    BufferingComputation(ChecksumCalculator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (WARNED.compareAndSet(false, true)) {
            log.warn("ChecksumCalculator {} does not override newComputation(); "
                    + "falling back to unbounded in-memory buffering. Override "
                    + "newComputation() to avoid potential OOM on large files.",
                    delegate.getClass().getName());
        }
    }

    @Override
    public void update(byte[] buf, int off, int len) {
        if (buffer == null) {
            throw new IllegalStateException("ChecksumComputation already finished");
        }
        buffer.write(buf, off, len);
    }

    @Override
    public String finish() {
        if (buffer == null) {
            throw new IllegalStateException("ChecksumComputation already finished");
        }
        String result = delegate.checksum(buffer.toByteArray());
        buffer = null;
        return result;
    }

}
