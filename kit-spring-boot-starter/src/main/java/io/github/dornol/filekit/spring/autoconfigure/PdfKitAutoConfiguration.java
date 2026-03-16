package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.pdf.PdfBoxMetadataExtractor;
import io.github.dornol.filekit.pdf.PdfMetadataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for PDF processing features.
 * Only activated when Apache PDFBox is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.apache.pdfbox.pdmodel.PDDocument")
public class PdfKitAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PdfKitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PdfMetadataExtractor pdfMetadataExtractor() {
        log.debug("Registering default PdfBoxMetadataExtractor");
        return new PdfBoxMetadataExtractor();
    }

}
