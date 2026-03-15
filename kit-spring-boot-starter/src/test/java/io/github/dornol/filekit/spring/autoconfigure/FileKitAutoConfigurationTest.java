package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector;
import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileKitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileKitAutoConfiguration.class));

    // ── Default beans registration ──────────────────────────────────

    @Test
    void registersAllBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MediaTypeDetector.class);
            assertThat(context).hasSingleBean(FileValidationHelper.class);
            assertThat(context).hasSingleBean(MultipartFileValidator.class);
            assertThat(context).hasSingleBean(MultipartFileArrayValidator.class);
            assertThat(context).hasSingleBean(MultipartFileCollectionValidator.class);
        });
    }

    // ── Tika detection (Tika is on test classpath) ──────────────────

    @Test
    void registersTikaDetector_whenTikaOnClasspath() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MediaTypeDetector.class);
            assertThat(context.getBean(MediaTypeDetector.class))
                    .isInstanceOf(TikaMediaTypeDetector.class);
        });
    }

    // ── User-defined bean takes priority ────────────────────────────

    @Test
    void userDefinedDetector_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomDetectorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaTypeDetector.class);
                    assertThat(context.getBean(MediaTypeDetector.class))
                            .isInstanceOf(CustomMediaTypeDetector.class);
                });
    }

    @Test
    void userDefinedHelper_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomHelperConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileValidationHelper.class);
                    assertThat(context.getBean(FileValidationHelper.class))
                            .isSameAs(context.getBean("customHelper"));
                });
    }

    @Test
    void userDefinedValidator_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomValidatorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MultipartFileValidator.class);
                    assertThat(context.getBean(MultipartFileValidator.class))
                            .isSameAs(context.getBean("customValidator"));
                });
    }

    // ── FileValidationHelper uses the registered detector ───────────

    @Test
    void fileValidationHelper_usesRegisteredDetector() {
        contextRunner
                .withUserConfiguration(CustomDetectorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileValidationHelper.class);
                    // helper should be created with the custom detector
                    assertThat(context).hasSingleBean(MediaTypeDetector.class);
                    assertThat(context.getBean(MediaTypeDetector.class))
                            .isInstanceOf(CustomMediaTypeDetector.class);
                });
    }

    // ── Test configurations ─────────────────────────────────────────

    @Configuration
    static class CustomDetectorConfig {
        @Bean
        MediaTypeDetector customDetector() {
            return new CustomMediaTypeDetector();
        }
    }

    @Configuration
    static class CustomHelperConfig {
        @Bean
        FileValidationHelper customHelper(MediaTypeDetector detector) {
            return new FileValidationHelper(detector);
        }
    }

    @Configuration
    static class CustomValidatorConfig {
        @Bean
        MultipartFileValidator customValidator(FileValidationHelper helper) {
            return new MultipartFileValidator(helper);
        }
    }

    static class CustomMediaTypeDetector implements MediaTypeDetector {
        @Override
        public String detect(String filename, InputStream inputStream) {
            return "application/custom";
        }
    }

}
