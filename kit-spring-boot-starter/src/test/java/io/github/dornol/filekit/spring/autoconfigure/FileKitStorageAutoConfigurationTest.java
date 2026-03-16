package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.scan.ScanResult;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spring.download.SpringDownloadService;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.storage.FileUploadCommand;
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
            assertThat(context).doesNotHaveBean(SpringDownloadService.class);
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
                    assertThat(context).hasSingleBean(SpringDownloadService.class);
                });
    }

    @Test
    void downloadBeans_registered_withOnlyRepositoryAndStorage() {
        contextRunner
                .withUserConfiguration(RepositoryOnlyConfig.class, FileStorageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageResolver.class);
                    assertThat(context).hasSingleBean(FileDownloadService.class);
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

    // ── Test configurations ─────────────────────────────────────────

    @Configuration
    static class AllPortsConfig {
        @Bean ChecksumCalculator checksumCalculator() { return bytes -> "checksum"; }
        @Bean FileMetadataRepository fileMetadataRepository() {
            return new FileMetadataRepository() {
                @Override public FileMetadata findByChecksum(String checksum) { return null; }
                @Override public FileMetadata findByKey(String key) { return null; }
                @Override public FileMetadata save(FileMetadata metadata) { return metadata; }
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

}
