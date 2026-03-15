package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.validator.FileSourceValidatorHelper;
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
    public FileSourceValidatorHelper fileSourceValidatorHelper(MediaTypeDetector detector) {
        return new FileSourceValidatorHelper(detector);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileValidator multipartFileValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileValidator(helper, converter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileArrayValidator multipartFileArrayValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileArrayValidator(helper, converter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartFileCollectionValidator multipartFileCollectionValidator(FileSourceValidatorHelper helper, MessageConverter converter) {
        return new MultipartFileCollectionValidator(helper, converter);
    }
}
