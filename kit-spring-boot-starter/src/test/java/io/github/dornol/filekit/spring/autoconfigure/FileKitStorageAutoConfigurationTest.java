package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileEventListener;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.QuotaPolicy;
import io.github.dornol.filekit.spi.QuotaUsageProvider;
import io.github.dornol.filekit.spring.download.SpringDownloadService;
import io.github.dornol.filekit.spring.actuate.FileKitStorageHealthIndicator;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
import io.github.dornol.filekit.transfer.FileTransferService;
import io.github.dornol.filekit.upload.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileKitStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileKitAutoConfiguration.class));

    enum TestStorageType { LOCAL }

    @Test
    void storageBeans_notRegistered_whenNoPortBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(FileStorageResolver.class);
            assertThat(context).doesNotHaveBean(FileUploadService.class);
            assertThat(context).doesNotHaveBean(FileDownloadService.class);
            assertThat(context).doesNotHaveBean(FileDeleteService.class);
            assertThat(context).doesNotHaveBean(SpringDownloadService.class);
            assertThat(context).doesNotHaveBean(FileKitStorageHealthIndicator.class);
        });
    }

    @Test
    void storageBeans_notRegistered_whenNoFileStorageBean() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageResolver.class);
                    assertThat(context).doesNotHaveBean(FileUploadService.class);
                    assertThat(context).doesNotHaveBean(FileDownloadService.class);
                    assertThat(context).doesNotHaveBean(FileDeleteService.class);
                    assertThat(context).doesNotHaveBean(SpringDownloadService.class);
                });
    }

    @Test
    void allStorageBeans_registered_whenPortsAndStoragePresent() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageResolver.class);
                    assertThat(context).hasSingleBean(FileUploadService.class);
                    assertThat(context).hasSingleBean(FileDownloadService.class);
                    assertThat(context).hasSingleBean(FileDeleteService.class);
                    assertThat(context).hasSingleBean(SpringDownloadService.class);
                    assertThat(context).hasSingleBean(FileKitStorageHealthIndicator.class);
                });
    }

    @Test
    void downloadBeans_registered_withOnlyRepositoryAndStorage() {
        contextRunner
                .withUserConfiguration(RepositoryOnlyConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageResolver.class);
                    assertThat(context).hasSingleBean(FileDownloadService.class);
                    assertThat(context).hasSingleBean(FileDeleteService.class);
                    assertThat(context).hasSingleBean(SpringDownloadService.class);
                    assertThat(context).doesNotHaveBean(FileUploadService.class);
                });
    }

    @Test
    void uploadService_registeredWithoutVirusScanner() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileUploadService.class);
                    assertThat(context).doesNotHaveBean(VirusScanner.class);
                });
    }

    @Test
    void uploadService_registeredWithVirusScanner() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class, FileStorageConfig.class, VirusScannerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileUploadService.class);
                    assertThat(context).hasSingleBean(VirusScanner.class);
                });
    }

    @Test
    void transferService_registered_whenPortsAndStoragePresent() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileTransferService.class);
                });
    }

    @Test
    void transferService_notRegistered_whenNoPortBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(FileTransferService.class);
        });
    }

    @Test
    void transferService_notRegistered_whenOnlyRepositoryPresent() {
        contextRunner
                .withUserConfiguration(RepositoryOnlyConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileTransferService.class);
                });
    }

    @Test
    void transferService_registered_withRepositoryAndStorage() {
        contextRunner
                .withUserConfiguration(RepositoryOnlyConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileTransferService.class);
                });
    }

    // ── QuotaChecker bean conditions ────────────────────────────────

    @Test
    void quotaChecker_notRegistered_whenNoPolicyOrProvider() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(QuotaChecker.class);
        });
    }

    @Test
    void quotaChecker_notRegistered_whenOnlyPolicyPresent() {
        contextRunner
                .withUserConfiguration(QuotaPolicyOnlyConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QuotaChecker.class);
                });
    }

    @Test
    void quotaChecker_registered_whenPolicyAndProviderPresent() {
        contextRunner
                .withUserConfiguration(QuotaConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QuotaChecker.class);
                });
    }

    // ── FileEventPublisher bean ─────────────────────────────────────

    @Test
    void eventPublisher_alwaysRegistered() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileEventPublisher.class);
        });
    }

    @Test
    void eventPublisher_registeredWithZeroListeners() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileEventPublisher.class);
            assertThat(context).doesNotHaveBean(FileEventListener.class);
        });
    }

    @Test
    void eventPublisher_registeredWithListeners() {
        contextRunner
                .withUserConfiguration(EventListenerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileEventPublisher.class);
                    assertThat(context).hasSingleBean(FileEventListener.class);
                });
    }

    // ── Service beans include quota and event ───────────────────────

    @Test
    void serviceBeans_createdWithQuotaAndEvent() {
        contextRunner
                .withUserConfiguration(AllPortsConfig.class, FileStorageConfig.class,
                        QuotaConfig.class, EventListenerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileUploadService.class);
                    assertThat(context).hasSingleBean(FileDownloadService.class);
                    assertThat(context).hasSingleBean(FileDeleteService.class);
                    assertThat(context).hasSingleBean(FileTransferService.class);
                    assertThat(context).hasSingleBean(QuotaChecker.class);
                    assertThat(context).hasSingleBean(FileEventPublisher.class);
                });
    }

    // ── Test configurations ─────────────────────────────────────────

    @Configuration
    static class AllPortsConfig {
        @Bean ChecksumCalculator checksumCalculator() { return bytes -> "checksum"; }
        @Bean FileMetadataRepository fileMetadataRepository() {
            return new FileMetadataRepository() {
                @Override public FileMetadata findByChecksum(String checksum) { return null; }
                @Override public FileMetadata findByKey(String key) { return null; }
                @Override public FileMetadata save(FileMetadata metadata) { return metadata; }
                @Override public void deleteByKey(String key) {}
            };
        }
        @Bean FileFormatExtractor fileFormatExtractor() {
            return is -> new FileFormat("application/octet-stream", "bin", "application");
        }
    }

    @Configuration
    static class RepositoryOnlyConfig {
        @Bean FileMetadataRepository fileMetadataRepository() {
            return new FileMetadataRepository() {
                @Override public FileMetadata findByChecksum(String checksum) { return null; }
                @Override public FileMetadata findByKey(String key) { return null; }
                @Override public FileMetadata save(FileMetadata metadata) { return metadata; }
                @Override public void deleteByKey(String key) {}
            };
        }
    }

    @Configuration
    static class VirusScannerConfig {
        @Bean VirusScanner virusScanner() {
            return bytes -> ScanResult.clean();
        }
    }

    @Configuration
    static class FileStorageConfig {
        @Bean FileStorage testFileStorage() {
            return new FileStorage() {
                @Override public Enum<?> getStorageType() { return TestStorageType.LOCAL; }
                @Override public FileLocation upload(FileUploadCommand command) {
                    return new FileLocation("bucket", command.key(), TestStorageType.LOCAL);
                }
                @Override public void delete(FileMetadata metadata) {}
                @Override public InputStream load(FileMetadata metadata) {
                    return InputStream.nullInputStream();
                }
                @Override public String resolveUri(FileMetadata metadata) { return ""; }
            };
        }
    }

    @Configuration
    static class QuotaPolicyOnlyConfig {
        @Bean QuotaPolicy quotaPolicy() {
            return (storageType, bucket) -> 1000L;
        }
    }

    @Configuration
    static class QuotaConfig {
        @Bean QuotaPolicy quotaPolicy() {
            return (storageType, bucket) -> 1000L;
        }
        @Bean QuotaUsageProvider quotaUsageProvider() {
            return (storageType, bucket) -> 0L;
        }
    }

    @Configuration
    static class EventListenerConfig {
        @Bean FileEventListener testListener() {
            return new FileEventListener() {};
        }
    }

}
