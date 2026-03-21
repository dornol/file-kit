package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileKitMetricsTest {

    private MeterRegistry registry;
    private FileKitMetrics metrics;

    enum TestStorage { LOCAL, S3 }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new FileKitMetrics(registry);
    }

    private FileMetadata metadata(TestStorage storage, String bucket, long size) {
        return new FileMetadata("key-1", "file.jpg", size, "sha256",
                new FileFormat("image/jpeg", "jpg", "image"),
                new FileLocation(bucket, "key-1.jpg", storage));
    }

    @Test
    void onUploaded_incrementsCounterAndRecordsSize() {
        metrics.onUploaded(metadata(TestStorage.LOCAL, "uploads", 1024));

        Counter counter = registry.find("file.kit.uploads")
                .tag("storageType", "LOCAL")
                .tag("bucket", "uploads")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());

        DistributionSummary summary = registry.find("file.kit.upload.size")
                .tag("storageType", "LOCAL")
                .tag("bucket", "uploads")
                .summary();
        assertNotNull(summary);
        assertEquals(1, summary.count());
        assertEquals(1024.0, summary.totalAmount());
    }

    @Test
    void onUploaded_multipleFiles_accumulates() {
        metrics.onUploaded(metadata(TestStorage.S3, "bucket-a", 1000));
        metrics.onUploaded(metadata(TestStorage.S3, "bucket-a", 2000));
        metrics.onUploaded(metadata(TestStorage.S3, "bucket-a", 3000));

        Counter counter = registry.find("file.kit.uploads")
                .tag("storageType", "S3")
                .tag("bucket", "bucket-a")
                .counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count());

        DistributionSummary summary = registry.find("file.kit.upload.size")
                .tag("storageType", "S3")
                .tag("bucket", "bucket-a")
                .summary();
        assertNotNull(summary);
        assertEquals(3, summary.count());
        assertEquals(6000.0, summary.totalAmount());
    }

    @Test
    void onDownloaded_incrementsCounter() {
        metrics.onDownloaded(metadata(TestStorage.LOCAL, "uploads", 512));

        Counter counter = registry.find("file.kit.downloads")
                .tag("storageType", "LOCAL")
                .tag("bucket", "uploads")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void onDeleted_incrementsCounter() {
        metrics.onDeleted(metadata(TestStorage.S3, "archive", 256));

        Counter counter = registry.find("file.kit.deletes")
                .tag("storageType", "S3")
                .tag("bucket", "archive")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void onCopied_incrementsCounterWithCopyMetadata() {
        FileMetadata source = metadata(TestStorage.LOCAL, "uploads", 100);
        FileMetadata copy = metadata(TestStorage.S3, "archive", 100);

        metrics.onCopied(source, copy);

        Counter counter = registry.find("file.kit.copies")
                .tag("storageType", "S3")
                .tag("bucket", "archive")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());

        // Source storage should not have a copies counter
        assertNull(registry.find("file.kit.copies")
                .tag("storageType", "LOCAL")
                .counter());
    }

    @Test
    void onMoved_incrementsCounterWithMovedMetadata() {
        FileMetadata source = metadata(TestStorage.LOCAL, "uploads", 100);
        FileMetadata moved = metadata(TestStorage.S3, "archive", 100);

        metrics.onMoved(source, moved);

        Counter counter = registry.find("file.kit.moves")
                .tag("storageType", "S3")
                .tag("bucket", "archive")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void differentBuckets_trackedSeparately() {
        metrics.onUploaded(metadata(TestStorage.LOCAL, "bucket-a", 100));
        metrics.onUploaded(metadata(TestStorage.LOCAL, "bucket-b", 200));

        Counter counterA = registry.find("file.kit.uploads")
                .tag("bucket", "bucket-a")
                .counter();
        Counter counterB = registry.find("file.kit.uploads")
                .tag("bucket", "bucket-b")
                .counter();
        assertNotNull(counterA);
        assertNotNull(counterB);
        assertEquals(1.0, counterA.count());
        assertEquals(1.0, counterB.count());
    }

    @Test
    void differentStorageTypes_trackedSeparately() {
        metrics.onUploaded(metadata(TestStorage.LOCAL, "uploads", 100));
        metrics.onUploaded(metadata(TestStorage.S3, "uploads", 200));

        Counter local = registry.find("file.kit.uploads")
                .tag("storageType", "LOCAL")
                .counter();
        Counter s3 = registry.find("file.kit.uploads")
                .tag("storageType", "S3")
                .counter();
        assertNotNull(local);
        assertNotNull(s3);
        assertEquals(1.0, local.count());
        assertEquals(1.0, s3.count());
    }

}
