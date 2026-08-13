package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.local.LocalFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes old files from the example's local storage when they have no
 * committed metadata record.
 *
 * <p>This maintenance job is deliberately opt-in and local-storage-only.
 * Object stores need a provider-specific listing implementation and a
 * repository lifecycle policy before they can be cleaned safely.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.orphan-cleanup", name = "enabled", havingValue = "true")
public class LocalOrphanCleanupService {

    private final FileMetadataRepositoryAdapter metadataRepository;
    private final LocalFileStorage localStorage;
    private final Duration gracePeriod;

    public LocalOrphanCleanupService(FileMetadataRepositoryAdapter metadataRepository,
                                     LocalFileStorage localStorage,
                                     @Value("${app.orphan-cleanup.grace-period:24h}") String gracePeriod) {
        this.metadataRepository = metadataRepository;
        this.localStorage = localStorage;
        Duration parsedGracePeriod = DurationStyle.detectAndParse(gracePeriod);
        if (parsedGracePeriod.isNegative() || parsedGracePeriod.isZero()) {
            throw new IllegalArgumentException("gracePeriod must be positive");
        }
        this.gracePeriod = parsedGracePeriod;
    }

    /** Runs the cleanup on the configured fixed delay. */
    @Scheduled(fixedDelayString = "${app.orphan-cleanup.fixed-delay-ms:3600000}")
    public void scheduledCleanup() {
        try {
            CleanupResult result = cleanup();
            if (result.deleted() > 0 || result.failed() > 0) {
                System.getLogger(getClass().getName()).log(
                        System.Logger.Level.INFO, "Local orphan cleanup: {0}", result);
            }
        } catch (IOException e) {
            System.getLogger(getClass().getName()).log(
                    System.Logger.Level.WARNING, "Local orphan cleanup failed", e);
        }
    }

    /**
     * Deletes eligible orphan files once.
     *
     * @return counts of examined, deleted, and failed files
     * @throws IOException if the storage root cannot be traversed
     */
    public CleanupResult cleanup() throws IOException {
        Path root = localStorage.baseDirectory();
        Instant cutoff = Instant.now().minus(gracePeriod);
        Set<Path> referenced = referencedPaths(root);
        int examined = 0;
        int deleted = 0;
        int failed = 0;

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                examined++;
                if (referenced.contains(path) || Files.isSymbolicLink(path)
                        || Files.getLastModifiedTime(path).toInstant().isAfter(cutoff)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                    deleted++;
                } catch (IOException e) {
                    failed++;
                }
            }
        }
        return new CleanupResult(examined, deleted, failed);
    }

    private Set<Path> referencedPaths(Path root) {
        Set<Path> paths = new HashSet<>();
        for (FileMetadata metadata : metadataRepository.findAll()) {
            if (metadata.location().storageType() != StorageType.LOCAL) {
                continue;
            }
            Path path = root.resolve(metadata.location().bucket())
                    .resolve(metadata.location().objectKey())
                    .toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** Result of one local orphan cleanup pass. */
    public record CleanupResult(int examined, int deleted, int failed) {}
}
