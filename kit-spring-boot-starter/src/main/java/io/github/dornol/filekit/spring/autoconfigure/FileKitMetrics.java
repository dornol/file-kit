package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer metrics collector for file-kit operations.
 *
 * <p>Implements {@link FileEventListener} to automatically record metrics
 * for file uploads, downloads, deletions, copies, and moves.</p>
 *
 * <p>Recorded metrics:</p>
 * <ul>
 *   <li>{@code file.kit.uploads} &mdash; Counter of uploaded files (tags: storageType, bucket)</li>
 *   <li>{@code file.kit.upload.size} &mdash; Distribution summary of uploaded file sizes in bytes</li>
 *   <li>{@code file.kit.downloads} &mdash; Counter of downloaded files (tags: storageType, bucket)</li>
 *   <li>{@code file.kit.deletes} &mdash; Counter of deleted files (tags: storageType, bucket)</li>
 *   <li>{@code file.kit.copies} &mdash; Counter of copied files (tags: storageType, bucket)</li>
 *   <li>{@code file.kit.moves} &mdash; Counter of moved files (tags: storageType, bucket)</li>
 * </ul>
 *
 * <p>Auto-configured when {@code MeterRegistry} is available on the classpath.
 * No configuration needed &mdash; just add {@code spring-boot-starter-actuator}.</p>
 */
public class FileKitMetrics implements FileEventListener {

    private static final String PREFIX = "file.kit.";

    private final MeterRegistry registry;
    private final boolean includeBucket;

    public FileKitMetrics(MeterRegistry registry) {
        this(registry, false);
    }

    public FileKitMetrics(MeterRegistry registry, boolean includeBucket) {
        this.registry = registry;
        this.includeBucket = includeBucket;
    }

    @Override
    public void onUploaded(FileMetadata metadata) {
        counter("uploads", metadata).increment();
        uploadSize(metadata).record(metadata.size());
    }

    @Override
    public void onDownloaded(FileMetadata metadata) {
        counter("downloads", metadata).increment();
    }

    @Override
    public void onDeleted(FileMetadata metadata) {
        counter("deletes", metadata).increment();
    }

    @Override
    public void onCopied(FileMetadata source, FileMetadata copy) {
        counter("copies", copy).increment();
    }

    @Override
    public void onMoved(FileMetadata source, FileMetadata moved) {
        counter("moves", moved).increment();
    }

    private Counter counter(String name, FileMetadata metadata) {
        Counter.Builder builder = Counter.builder(PREFIX + name)
                .tag("storageType", metadata.location().storageType().name());
        if (includeBucket) builder.tag("bucket", metadata.location().bucket());
        return builder.register(registry);
    }

    private DistributionSummary uploadSize(FileMetadata metadata) {
        DistributionSummary.Builder builder = DistributionSummary.builder(PREFIX + "upload.size")
                .tag("storageType", metadata.location().storageType().name())
                .baseUnit("bytes");
        if (includeBucket) builder.tag("bucket", metadata.location().bucket());
        return builder.register(registry);
    }

}
