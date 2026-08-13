package io.github.dornol.filekit.spring.autoconfigure;

import io.github.dornol.filekit.archive.ArchiveMetadata;
import io.github.dornol.filekit.archive.ArchiveMetadataExtractor;
import io.github.dornol.filekit.archive.ZipArchiveMetadataExtractor;
import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.image.ConvertOption;
import io.github.dornol.filekit.image.ConvertResult;
import io.github.dornol.filekit.image.DefaultThumbnailGenerator;
import io.github.dornol.filekit.image.ExifStripper;
import io.github.dornol.filekit.image.ImageFormatConverter;
import io.github.dornol.filekit.image.ImageIOExifStripper;
import io.github.dornol.filekit.image.ImageIOFormatConverter;
import io.github.dornol.filekit.image.ImageIOMetadataExtractor;
import io.github.dornol.filekit.image.ImageIOResizer;
import io.github.dornol.filekit.image.ImageIOWatermarker;
import io.github.dornol.filekit.image.ImageMetadata;
import io.github.dornol.filekit.image.ImageMetadataExtractor;
import io.github.dornol.filekit.image.ImageResizer;
import io.github.dornol.filekit.image.ImageWatermarker;
import io.github.dornol.filekit.image.ResizeOption;
import io.github.dornol.filekit.image.ResizeResult;
import io.github.dornol.filekit.image.ThumbnailGenerator;
import io.github.dornol.filekit.image.ThumbnailOption;
import io.github.dornol.filekit.image.WatermarkOption;
import io.github.dornol.filekit.image.WatermarkPosition;
import io.github.dornol.filekit.image.WatermarkResult;
import io.github.dornol.filekit.spi.ChecksumCalculator;
import io.github.dornol.filekit.spi.Sha256ChecksumCalculator;
import io.github.dornol.filekit.spring.validator.MultipartFileArrayValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileCollectionValidator;
import io.github.dornol.filekit.spring.validator.MultipartFileValidator;
import io.github.dornol.filekit.spring.validator.TikaMediaTypeDetector;
import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
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

    @Test
    void mvcOnlyRuntime_doesNotLoadReactiveAdapterWithoutReactor() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("reactor.core"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    // ── ChecksumCalculator auto-registration ───────────────────────

    @Test
    void registersDefaultChecksumCalculator() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChecksumCalculator.class);
            assertThat(context.getBean(ChecksumCalculator.class))
                    .isInstanceOf(Sha256ChecksumCalculator.class);
        });
    }

    @Test
    void userDefinedChecksumCalculator_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomChecksumConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChecksumCalculator.class);
                    assertThat(context.getBean(ChecksumCalculator.class))
                            .isSameAs(context.getBean("customChecksum"));
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

    // ── Image processing beans ──────────────────────────────────────

    @Test
    void registersDefaultImageMetadataExtractor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageMetadataExtractor.class);
            assertThat(context.getBean(ImageMetadataExtractor.class))
                    .isInstanceOf(ImageIOMetadataExtractor.class);
        });
    }

    @Test
    void registersDefaultImageResizer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageResizer.class);
            assertThat(context.getBean(ImageResizer.class))
                    .isInstanceOf(ImageIOResizer.class);
        });
    }

    @Test
    void userDefinedImageMetadataExtractor_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomImageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageMetadataExtractor.class);
                    assertThat(context.getBean(ImageMetadataExtractor.class))
                            .isSameAs(context.getBean("customImageMetadataExtractor"));
                });
    }

    @Test
    void userDefinedImageResizer_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomImageConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageResizer.class);
                    assertThat(context.getBean(ImageResizer.class))
                            .isSameAs(context.getBean("customImageResizer"));
                });
    }

    // ── Watermark bean ──────────────────────────────────────────────

    @Test
    void registersDefaultImageWatermarker() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageWatermarker.class);
            assertThat(context.getBean(ImageWatermarker.class))
                    .isInstanceOf(ImageIOWatermarker.class);
        });
    }

    @Test
    void userDefinedImageWatermarker_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomWatermarkerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageWatermarker.class);
                    assertThat(context.getBean(ImageWatermarker.class))
                            .isSameAs(context.getBean("customWatermarker"));
                });
    }

    // ── Thumbnail bean ──────────────────────────────────────────────

    @Test
    void registersDefaultThumbnailGenerator() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ThumbnailGenerator.class);
            assertThat(context.getBean(ThumbnailGenerator.class))
                    .isInstanceOf(DefaultThumbnailGenerator.class);
        });
    }

    @Test
    void userDefinedThumbnailGenerator_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomThumbnailConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ThumbnailGenerator.class);
                    assertThat(context.getBean(ThumbnailGenerator.class))
                            .isSameAs(context.getBean("customThumbnailGenerator"));
                });
    }

    // ── FileEncryptor bean ──────────────────────────────────────────

    @Test
    void registersDefaultFileEncryptor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileEncryptor.class);
            assertThat(context.getBean(FileEncryptor.class))
                    .isInstanceOf(NoOpFileEncryptor.class);
        });
    }

    @Test
    void encryptionRequired_withoutCustomEncryptor_failsStartup() {
        contextRunner
                .withPropertyValues("file-kit.encryption-required=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("file-kit.encryption-required=true but no FileEncryptor bean is configured");
                });
    }

    @Test
    void userDefinedFileEncryptor_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomFileEncryptorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileEncryptor.class);
                    assertThat(context.getBean(FileEncryptor.class))
                            .isSameAs(context.getBean("customFileEncryptor"));
                });
    }

    // ── Archive bean ────────────────────────────────────────────────

    @Test
    void registersDefaultArchiveMetadataExtractor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ArchiveMetadataExtractor.class);
            assertThat(context.getBean(ArchiveMetadataExtractor.class))
                    .isInstanceOf(ZipArchiveMetadataExtractor.class);
        });
    }

    @Test
    void userDefinedArchiveMetadataExtractor_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomArchiveConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ArchiveMetadataExtractor.class);
                    assertThat(context.getBean(ArchiveMetadataExtractor.class))
                            .isSameAs(context.getBean("customArchiveExtractor"));
                });
    }

    // ── ExifStripper bean ──────────────────────────────────────────

    @Test
    void registersDefaultExifStripper() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ExifStripper.class);
            assertThat(context.getBean(ExifStripper.class))
                    .isInstanceOf(ImageIOExifStripper.class);
        });
    }

    @Test
    void userDefinedExifStripper_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomExifStripperConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ExifStripper.class);
                    assertThat(context.getBean(ExifStripper.class))
                            .isSameAs(context.getBean("customExifStripper"));
                });
    }

    // ── ImageFormatConverter bean ──────────────────────────────────

    @Test
    void registersDefaultImageFormatConverter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageFormatConverter.class);
            assertThat(context.getBean(ImageFormatConverter.class))
                    .isInstanceOf(ImageIOFormatConverter.class);
        });
    }

    @Test
    void userDefinedImageFormatConverter_takesPriority() {
        contextRunner
                .withUserConfiguration(CustomFormatConverterConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageFormatConverter.class);
                    assertThat(context.getBean(ImageFormatConverter.class))
                            .isSameAs(context.getBean("customFormatConverter"));
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

    @Configuration
    static class CustomChecksumConfig {
        @Bean
        ChecksumCalculator customChecksum() {
            return bytes -> "custom";
        }
    }

    @Configuration
    static class CustomImageConfig {
        @Bean
        ImageMetadataExtractor customImageMetadataExtractor() {
            return imageBytes -> new ImageMetadata(1, 1, "custom");
        }

        @Bean
        ImageResizer customImageResizer() {
            return (imageBytes, option) -> new ResizeResult(
                    new byte[0], new ImageMetadata(1, 1, "custom"));
        }
    }

    @Configuration
    static class CustomWatermarkerConfig {
        @Bean
        ImageWatermarker customWatermarker() {
            return (imageBytes, option) -> new WatermarkResult(
                    new byte[0], new ImageMetadata(1, 1, "custom"));
        }
    }

    @Configuration
    static class CustomThumbnailConfig {
        @Bean
        ThumbnailGenerator customThumbnailGenerator() {
            return (imageBytes, option) -> new ResizeResult(
                    new byte[0], new ImageMetadata(1, 1, "custom"));
        }
    }

    @Configuration
    static class CustomFileEncryptorConfig {
        @Bean
        FileEncryptor customFileEncryptor() {
            return new NoOpFileEncryptor();
        }
    }

    @Configuration
    static class CustomArchiveConfig {
        @Bean
        ArchiveMetadataExtractor customArchiveExtractor() {
            return archiveBytes -> new ArchiveMetadata(0, 0, java.util.List.of());
        }
    }

    @Configuration
    static class CustomExifStripperConfig {
        @Bean
        ExifStripper customExifStripper() {
            return new ExifStripper() {
                @Override
                public byte[] strip(byte[] imageBytes) { return imageBytes; }
                @Override
                public byte[] strip(byte[] imageBytes, float quality) { return imageBytes; }
            };
        }
    }

    @Configuration
    static class CustomFormatConverterConfig {
        @Bean
        ImageFormatConverter customFormatConverter() {
            return (imageBytes, option) -> new ConvertResult(
                    new byte[0], new ImageMetadata(1, 1, "custom"));
        }
    }

    static class CustomMediaTypeDetector implements MediaTypeDetector {
        @Override
        public String detect(String filename, InputStream inputStream) {
            return "application/custom";
        }
    }

}
