package io.github.dornol.filekit.spring.actuate;

import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileUploadCommand;
import io.github.dornol.filekit.storage.StorageHealthCheck;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileKitStorageHealthIndicatorTest {

    enum StorageType { HEALTHY, BROKEN, PASSIVE }

    @Test
    void health_isUpWhenAllActiveStoragesAreAvailable() {
        var health = new FileKitStorageHealthIndicator(List.of(new HealthyStorage())).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("HEALTHY", "available");
    }

    @Test
    void health_isDownWhenAnActiveStorageFails() {
        var health = new FileKitStorageHealthIndicator(List.of(new BrokenStorage())).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("BROKEN", "unavailable");
    }

    @Test
    void health_isUpAndReportsPassiveStorageWithoutActiveProbe() {
        var health = new FileKitStorageHealthIndicator(List.of(new PassiveStorage())).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("PASSIVE", "no active health probe");
    }

    private static class HealthyStorage extends PassiveStorage implements StorageHealthCheck {
        @Override
        public void check() {
        }

        @Override
        public Enum<?> getStorageType() {
            return StorageType.HEALTHY;
        }
    }

    private static class BrokenStorage extends PassiveStorage implements StorageHealthCheck {
        @Override
        public void check() {
            throw new IllegalStateException("backend unavailable");
        }

        @Override
        public Enum<?> getStorageType() {
            return StorageType.BROKEN;
        }
    }

    private static class PassiveStorage implements FileStorage {
        @Override
        public Enum<?> getStorageType() {
            return StorageType.PASSIVE;
        }

        @Override
        public FileLocation upload(FileUploadCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream load(FileMetadata metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(FileMetadata metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveUri(FileMetadata metadata) {
            throw new UnsupportedOperationException();
        }
    }
}
