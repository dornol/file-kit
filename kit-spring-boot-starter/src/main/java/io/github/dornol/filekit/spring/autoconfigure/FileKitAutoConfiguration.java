package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import io.github.dornol.filekit.validator.MessageConverter;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(MediaTypeDetector.class)
public class FileKitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter fileKitMessageConverter() {
        return (key, args) -> key;
    }

    @Bean
    @ConditionalOnMissingBean
    public FileValidationHelper fileSourceValidatorHelper(MediaTypeDetector detector) {
        return new FileValidationHelper(detector);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileValidator multipartFileValidator(FileValidationHelper helper, MessageConverter converter) {
        return new MultipartFileValidator(helper, converter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileArrayValidator multipartFileArrayValidator(FileValidationHelper helper, MessageConverter converter) {
        return new MultipartFileArrayValidator(helper, converter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileCollectionValidator multipartFileCollectionValidator(FileValidationHelper helper, MessageConverter converter) {
        return new MultipartFileCollectionValidator(helper, converter);
    }
}
