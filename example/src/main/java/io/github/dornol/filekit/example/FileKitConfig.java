package io.github.dornol.filekit.example;

import io.github.dornol.filekit.validator.FileSourceValidatorHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import io.github.dornol.filekit.validator.MessageConverter;
import io.github.dornol.filekit.validator.TikaMediaTypeDetector;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileKitConfig {

    @Bean
    public MediaTypeDetector mediaTypeDetector() {
        return new TikaMediaTypeDetector();
    }

    @Bean
    public MessageConverter messageConverter() {
        return (key, args) -> key;
    }

    @Bean
    public FileSourceValidatorHelper fileSourceValidatorHelper(MediaTypeDetector detector) {
        return new FileSourceValidatorHelper(detector);
    }

    @Bean
    public MultipartFileValidator multipartFileValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileValidator(helper, converter);
    }

    @Bean
    public MultipartFileArrayValidator multipartFileArrayValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileArrayValidator(helper, converter);
    }

    @Bean
    public MultipartFileCollectionValidator multipartFileCollectionValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileCollectionValidator(helper, converter);
    }
}
