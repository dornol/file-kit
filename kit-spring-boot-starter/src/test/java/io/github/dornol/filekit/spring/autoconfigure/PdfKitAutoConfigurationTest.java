package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.pdf.PdfBoxMetadataExtractor;
import io.github.dornol.filekit.pdf.PdfMetadataExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PdfKitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PdfKitAutoConfiguration.class));

    @Test
    void registersDefaultPdfMetadataExtractor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PdfMetadataExtractor.class);
            assertThat(context.getBean(PdfMetadataExtractor.class))
                    .isInstanceOf(PdfBoxMetadataExtractor.class);
        });
    }

    @Test
    void userDefinedExtractor_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomPdfConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PdfMetadataExtractor.class);
                    assertThat(context.getBean(PdfMetadataExtractor.class))
                            .isSameAs(context.getBean("customPdfExtractor"));
                });
    }

    @Configuration
    static class CustomPdfConfig {
        @Bean
        PdfMetadataExtractor customPdfExtractor() {
            return pdfBytes -> new io.github.dornol.filekit.pdf.PdfMetadata(0, null, null, null, null);
        }
    }
}
