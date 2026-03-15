package io.github.dornol.filekit.example;

import io.github.dornol.filekit.validator.MediaTypeDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileKitConfig {

    @Bean
    public MediaTypeDetector mediaTypeDetector() {
        return new TikaMediaTypeDetector();
    }
}
