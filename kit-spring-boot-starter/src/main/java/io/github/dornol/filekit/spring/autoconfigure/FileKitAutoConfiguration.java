package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.archive.ArchiveMetadataExtractor;
import io.github.dornol.filekit.archive.ZipArchiveMetadataExtractor;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.event.FileEventPublisher;
import io.github.dornol.filekit.image.DefaultThumbnailGenerator;
import io.github.dornol.filekit.image.ExifStripper;
import io.github.dornol.filekit.image.ImageFormatConverter;
import io.github.dornol.filekit.image.ImageIOExifStripper;
import io.github.dornol.filekit.image.ImageIOFormatConverter;
import io.github.dornol.filekit.image.ImageIOMetadataExtractor;
import io.github.dornol.filekit.image.ImageIOResizer;
import io.github.dornol.filekit.image.ImageIOWatermarker;
import io.github.dornol.filekit.image.ImageMetadataExtractor;
import io.github.dornol.filekit.image.ImageResizer;
import io.github.dornol.filekit.image.ImageWatermarker;
import io.github.dornol.filekit.image.ThumbnailGenerator;
import io.github.dornol.filekit.quota.QuotaChecker;
import io.github.dornol.filekit.transfer.FileTransferService;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.FileEventListener;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.spi.QuotaPolicy;
import io.github.dornol.filekit.spi.QuotaUsageProvider;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.spring.download.SpringDownloadService;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector;
import io.github.dornol.filekit.storage.FileStorage;
import io.github.dornol.filekit.storage.FileStorageResolver;
import io.github.dornol.filekit.upload.FileUploadService;
import io.github.dornol.filekit.validator.DefaultMediaTypeDetector;
import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.List;

/**
 * Spring Boot auto-configuration for file-kit.
 *
 * <p>Automatically registers the following beans:</p>
 * <ul>
 *   <li>{@link MediaTypeDetector} &mdash; Tika-based (if on classpath), otherwise Java built-in</li>
 *   <li>{@link FileValidationHelper}</li>
 *   <li>{@link MultipartFileValidator}, {@link MultipartFileArrayValidator},
 *       {@link MultipartFileCollectionValidator}</li>
 *   <li>{@link FileStorageResolver}, {@link FileUploadService}, {@link FileDownloadService},
 *       {@link FileDeleteService}, {@link SpringDownloadService} &mdash; when port beans are available</li>
 *   <li>{@link QuotaChecker} &mdash; when both {@link QuotaPolicy} and {@link QuotaUsageProvider} are available</li>
 *   <li>{@link FileEventPublisher} &mdash; always registered, with any available {@link FileEventListener}s</li>
 * </ul>
 *
 * <p>All beans are {@code @ConditionalOnMissingBean}, so user-defined beans always take priority.</p>
 *
 * @see FileKitProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(FileKitProperties.class)
public class FileKitAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileKitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MediaTypeDetector mediaTypeDetector() {
        if (ClassUtils.isPresent("org.apache.tika.Tika", getClass().getClassLoader())) {
            log.info("Registering TikaMediaTypeDetector (Apache Tika detected on classpath)");
            return new TikaMediaTypeDetector();
        }
        log.warn("Registering DefaultMediaTypeDetector (Java URLConnection-based). "
                + "For better accuracy, add Apache Tika to your classpath.");
        return new DefaultMediaTypeDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public FileValidationHelper fileValidationHelper(MediaTypeDetector detector) {
        log.debug("Registering FileValidationHelper with {}", detector.getClass().getSimpleName());
        return new FileValidationHelper(detector);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileValidator multipartFileValidator(FileValidationHelper helper) {
        return new MultipartFileValidator(helper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileArrayValidator multipartFileArrayValidator(FileValidationHelper helper) {
        return new MultipartFileArrayValidator(helper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileCollectionValidator multipartFileCollectionValidator(FileValidationHelper helper) {
        return new MultipartFileCollectionValidator(helper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChecksumCalculator checksumCalculator() {
        log.debug("Registering default Sha256ChecksumCalculator");
        return new Sha256ChecksumCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(FileStorage.class)
    public FileStorageResolver fileStorageResolver(List<FileStorage> storages) {
        log.info("Registering FileStorageResolver with {} storage(s)", storages.size());
        return new FileStorageResolver(storages);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileEncryptor fileEncryptor() {
        log.debug("Registering default NoOpFileEncryptor");
        return new NoOpFileEncryptor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({QuotaPolicy.class, QuotaUsageProvider.class})
    public QuotaChecker quotaChecker(QuotaPolicy policy, QuotaUsageProvider provider) {
        log.info("Registering QuotaChecker");
        return new QuotaChecker(policy, provider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileEventPublisher fileEventPublisher(ObjectProvider<FileEventListener> listeners) {
        List<FileEventListener> list = listeners.orderedStream().toList();
        log.info("Registering FileEventPublisher with {} listener(s)", list.size());
        return new FileEventPublisher(list);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileFormatExtractor.class, FileStorageResolver.class})
    public FileUploadService fileUploadService(ChecksumCalculator checksumCalculator,
                                               FileMetadataRepository metadataRepository,
                                               FileFormatExtractor formatExtractor,
                                               FileStorageResolver storageResolver,
                                               FileKitProperties properties,
                                               ObjectProvider<VirusScanner> virusScannerProvider,
                                               FileEncryptor fileEncryptor,
                                               ObjectProvider<QuotaChecker> quotaCheckerProvider,
                                               FileEventPublisher eventPublisher) {
        long maxUploadSize = properties.getMaxUploadSize();
        VirusScanner virusScanner = virusScannerProvider.getIfAvailable();
        QuotaChecker quotaChecker = quotaCheckerProvider.getIfAvailable();
        log.info("Registering FileUploadService (maxUploadSize={}, virusScanner={}, encryption={}, quota={})",
                maxUploadSize == 0 ? "unlimited" : maxUploadSize,
                virusScanner != null ? virusScanner.getClass().getSimpleName() : "none",
                fileEncryptor.getClass().getSimpleName(),
                quotaChecker != null ? "enabled" : "none");
        return FileUploadService.builder(checksumCalculator, metadataRepository, formatExtractor, storageResolver)
                .maxUploadSize(maxUploadSize)
                .virusScanner(virusScanner)
                .fileEncryptor(fileEncryptor)
                .quotaChecker(quotaChecker)
                .eventPublisher(eventPublisher)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public FileDownloadService fileDownloadService(FileMetadataRepository metadataRepository,
                                                   FileStorageResolver storageResolver,
                                                   FileEncryptor fileEncryptor,
                                                   FileEventPublisher eventPublisher) {
        log.info("Registering FileDownloadService");
        return FileDownloadService.builder(metadataRepository, storageResolver)
                .fileEncryptor(fileEncryptor)
                .eventPublisher(eventPublisher)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public FileDeleteService fileDeleteService(FileMetadataRepository metadataRepository,
                                               FileStorageResolver storageResolver,
                                               FileEventPublisher eventPublisher) {
        log.info("Registering FileDeleteService");
        return FileDeleteService.builder(metadataRepository, storageResolver)
                .eventPublisher(eventPublisher)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public SpringDownloadService springDownloadService(FileMetadataRepository metadataRepository,
                                                       FileStorageResolver storageResolver,
                                                       FileEncryptor fileEncryptor) {
        log.info("Registering SpringDownloadService");
        return new SpringDownloadService(metadataRepository, storageResolver, fileEncryptor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageMetadataExtractor imageMetadataExtractor() {
        log.debug("Registering default ImageIOMetadataExtractor");
        return new ImageIOMetadataExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageResizer imageResizer(ImageMetadataExtractor metadataExtractor) {
        log.debug("Registering default ImageIOResizer");
        return new ImageIOResizer(metadataExtractor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageWatermarker imageWatermarker(ImageMetadataExtractor metadataExtractor) {
        log.debug("Registering default ImageIOWatermarker");
        return new ImageIOWatermarker(metadataExtractor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThumbnailGenerator thumbnailGenerator(ImageResizer imageResizer) {
        log.debug("Registering default DefaultThumbnailGenerator");
        return new DefaultThumbnailGenerator(imageResizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ArchiveMetadataExtractor archiveMetadataExtractor() {
        log.debug("Registering default ZipArchiveMetadataExtractor");
        return new ZipArchiveMetadataExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExifStripper exifStripper(ImageMetadataExtractor metadataExtractor) {
        log.debug("Registering default ImageIOExifStripper");
        return new ImageIOExifStripper(metadataExtractor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageFormatConverter imageFormatConverter() {
        log.debug("Registering default ImageIOFormatConverter");
        return new ImageIOFormatConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public FileTransferService fileTransferService(FileMetadataRepository metadataRepository,
                                                    FileStorageResolver storageResolver,
                                                    ObjectProvider<QuotaChecker> quotaCheckerProvider,
                                                    FileEventPublisher eventPublisher) {
        QuotaChecker quotaChecker = quotaCheckerProvider.getIfAvailable();
        log.info("Registering FileTransferService (quota={})", quotaChecker != null ? "enabled" : "none");
        return new FileTransferService(metadataRepository, storageResolver, quotaChecker, eventPublisher);
    }

    @Configuration
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        public FileKitMetrics fileKitMetrics(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            log.info("Registering FileKitMetrics (Micrometer)");
            return new FileKitMetrics(meterRegistry);
        }

    }

}
