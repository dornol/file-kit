package io.github.dornol.filekit.spring.actuate;

import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.StorageHealthCheck;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.List;

/** Actuator health indicator for file-kit storages that expose an active probe. */
public final class FileKitStorageHealthIndicator implements HealthIndicator {

    private final List<FileStorage> storages;

    public FileKitStorageHealthIndicator(List<FileStorage> storages) {
        this.storages = List.copyOf(storages);
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        for (FileStorage storage : storages) {
            if (!(storage instanceof StorageHealthCheck check)) {
                builder.withDetail(storage.getStorageType().name(), "no active health probe");
                continue;
            }
            try {
                check.check();
                builder.withDetail(storage.getStorageType().name(), "available");
            } catch (RuntimeException e) {
                return Health.down(e)
                        .withDetail(storage.getStorageType().name(), "unavailable")
                        .build();
            }
        }
        return builder.build();
    }
}
