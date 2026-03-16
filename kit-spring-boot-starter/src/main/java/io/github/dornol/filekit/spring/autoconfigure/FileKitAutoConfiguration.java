package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.image.ImageIOMetadataExtractor;
import io.github.dornol.filekit.image.ImageIOResizer;
import io.github.dornol.filekit.image.ImageMetadataExtractor;
import io.github.dornol.filekit.image.ImageResizer;
import io.github.dornol.filekit.scan.VirusScanner;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import io.github.dornol.filekit.spi.FileMetadataRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
        try {
            Class.forName("org.apache.tika.Tika");
            log.info("Registering TikaMediaTypeDetector (Apache Tika detected on classpath)");
            return new TikaMediaTypeDetector();
        } catch (ClassNotFoundException e) {
            log.warn("Registering DefaultMediaTypeDetector (Java URLConnection-based). "
                    + "For better accuracy, add Apache Tika to your classpath.");
            return new DefaultMediaTypeDetector();
        }
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
    @ConditionalOnBean({FileMetadataRepository.class, FileFormatExtractor.class, FileStorageResolver.class})
    public FileUploadService fileUploadService(ChecksumCalculator checksumCalculator,
                                               FileMetadataRepository metadataRepository,
                                               FileFormatExtractor formatExtractor,
                                               FileStorageResolver storageResolver,
                                               FileKitProperties properties,
                                               ObjectProvider<VirusScanner> virusScannerProvider) {
        long maxUploadSize = properties.getMaxUploadSize();
        VirusScanner virusScanner = virusScannerProvider.getIfAvailable();
        log.info("Registering FileUploadService (maxUploadSize={}, virusScanner={})",
                maxUploadSize == 0 ? "unlimited" : maxUploadSize,
                virusScanner != null ? virusScanner.getClass().getSimpleName() : "none");
        return new FileUploadService(checksumCalculator, metadataRepository, formatExtractor,
                storageResolver, maxUploadSize, virusScanner);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public FileDownloadService fileDownloadService(FileMetadataRepository metadataRepository,
                                                   FileStorageResolver storageResolver) {
        log.info("Registering FileDownloadService");
        return new FileDownloadService(metadataRepository, storageResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public FileDeleteService fileDeleteService(FileMetadataRepository metadataRepository,
                                               FileStorageResolver storageResolver) {
        log.info("Registering FileDeleteService");
        return new FileDeleteService(metadataRepository, storageResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({FileMetadataRepository.class, FileStorageResolver.class})
    public SpringDownloadService springDownloadService(FileMetadataRepository metadataRepository,
                                                       FileStorageResolver storageResolver) {
        log.info("Registering SpringDownloadService");
        return new SpringDownloadService(metadataRepository, storageResolver);
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

}
