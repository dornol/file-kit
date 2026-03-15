package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.validator.DefaultMediaTypeDetector;
import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for file-kit.
 *
 * <p>Automatically registers the following beans:</p>
 * <ul>
 *   <li>{@link MediaTypeDetector} &mdash; Tika-based (if on classpath), otherwise Java built-in</li>
 *   <li>{@link FileValidationHelper}</li>
 *   <li>{@link MultipartFileValidator}, {@link MultipartFileArrayValidator},
 *       {@link MultipartFileCollectionValidator}</li>
 * </ul>
 *
 * <p>All beans are {@code @ConditionalOnMissingBean}, so user-defined beans always take priority.</p>
 */
@AutoConfiguration
public class FileKitAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileKitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MediaTypeDetector mediaTypeDetector() {
        try {
            Class.forName("org.apache.tika.Tika");
            log.info("file-kit: Registering TikaMediaTypeDetector (Apache Tika detected on classpath)");
            return new TikaMediaTypeDetector();
        } catch (ClassNotFoundException e) {
            log.warn("file-kit: Registering DefaultMediaTypeDetector (Java URLConnection-based). "
                    + "For better accuracy, add Apache Tika to your classpath.");
            return new DefaultMediaTypeDetector();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public FileValidationHelper fileValidationHelper(MediaTypeDetector detector) {
        log.info("file-kit: Registering FileValidationHelper with {}", detector.getClass().getSimpleName());
        return new FileValidationHelper(detector);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileValidator multipartFileValidator(FileValidationHelper helper) {
        log.debug("file-kit: Registering MultipartFileValidator");
        return new MultipartFileValidator(helper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileArrayValidator multipartFileArrayValidator(FileValidationHelper helper) {
        log.debug("file-kit: Registering MultipartFileArrayValidator");
        return new MultipartFileArrayValidator(helper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileCollectionValidator multipartFileCollectionValidator(FileValidationHelper helper) {
        log.debug("file-kit: Registering MultipartFileCollectionValidator");
        return new MultipartFileCollectionValidator(helper);
    }
}
