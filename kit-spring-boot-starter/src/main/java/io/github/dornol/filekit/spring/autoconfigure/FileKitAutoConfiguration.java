package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.validator.DefaultMediaTypeDetector;
import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class FileKitAutoConfiguration {

    @Configuration
    @ConditionalOnClass(name = "org.apache.tika.Tika")
    @ConditionalOnMissingBean(MediaTypeDetector.class)
    static class TikaDetectorConfiguration {

        @Bean
        public MediaTypeDetector tikaMediaTypeDetector() {
            return new TikaMediaTypeDetector();
        }
    }

    @Configuration
    @ConditionalOnMissingBean(MediaTypeDetector.class)
    static class DefaultDetectorConfiguration {

        @Bean
        public MediaTypeDetector defaultMediaTypeDetector() {
            return new DefaultMediaTypeDetector();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public FileValidationHelper fileValidationHelper(MediaTypeDetector detector) {
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
}
