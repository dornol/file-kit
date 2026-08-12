package io.github.dornol.filekit.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileKitPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileKitAutoConfiguration.class));

    @Test
    void defaultMaxUploadSize_isZero() {
        FileKitProperties props = new FileKitProperties();
        assertThat(props.getMaxUploadSize()).isEqualTo(0L);
    }

    @Test
    void setAndGetMaxUploadSize() {
        FileKitProperties props = new FileKitProperties();
        props.setMaxUploadSize(10 * 1024 * 1024);
        assertThat(props.getMaxUploadSize()).isEqualTo(10 * 1024 * 1024);
    }

    @Test
    void negativeMaxUploadSize_throws() {
        FileKitProperties props = new FileKitProperties();
        assertThatThrownBy(() -> props.setMaxUploadSize(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void zeroMaxUploadSize_isAllowed() {
        FileKitProperties props = new FileKitProperties();
        props.setMaxUploadSize(0);
        assertThat(props.getMaxUploadSize()).isEqualTo(0L);
    }

    @Test
    void propertiesBound_fromApplicationConfig() {
        contextRunner
                .withPropertyValues("file-kit.max-upload-size=52428800")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileKitProperties.class);
                    FileKitProperties props = context.getBean(FileKitProperties.class);
                    assertThat(props.getMaxUploadSize()).isEqualTo(52428800L);
                });
    }

    @Test
    void propertiesBound_defaultWhenNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileKitProperties.class);
            FileKitProperties props = context.getBean(FileKitProperties.class);
            assertThat(props.getMaxUploadSize()).isEqualTo(0L);
        });
    }

    // ── verifyChecksumOnDownload ─────────────────────────────────────

    @Test
    void defaultVerifyChecksumOnDownload_isFalse() {
        FileKitProperties props = new FileKitProperties();
        assertThat(props.isVerifyChecksumOnDownload()).isFalse();
    }

    @Test
    void setAndGetVerifyChecksumOnDownload() {
        FileKitProperties props = new FileKitProperties();
        props.setVerifyChecksumOnDownload(true);
        assertThat(props.isVerifyChecksumOnDownload()).isTrue();
    }

    @Test
    void verifyChecksumOnDownload_boundFromConfig() {
        contextRunner
                .withPropertyValues("file-kit.verify-checksum-on-download=true")
                .run(context -> {
                    FileKitProperties props = context.getBean(FileKitProperties.class);
                    assertThat(props.isVerifyChecksumOnDownload()).isTrue();
                });
    }

    // ── maxPresignedExpiration ───────────────────────────────────────

    @Test
    void defaultMaxPresignedExpiration_isNull() {
        FileKitProperties props = new FileKitProperties();
        assertThat(props.getMaxPresignedExpiration()).isNull();
    }

    @Test
    void setAndGetMaxPresignedExpiration() {
        FileKitProperties props = new FileKitProperties();
        props.setMaxPresignedExpiration(Duration.ofHours(24));
        assertThat(props.getMaxPresignedExpiration()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void maxPresignedExpiration_null_isAllowed() {
        FileKitProperties props = new FileKitProperties();
        props.setMaxPresignedExpiration(Duration.ofHours(1));
        props.setMaxPresignedExpiration(null);
        assertThat(props.getMaxPresignedExpiration()).isNull();
    }

    @Test
    void maxPresignedExpiration_boundFromConfig() {
        contextRunner
                .withPropertyValues("file-kit.max-presigned-expiration=1h")
                .run(context -> {
                    FileKitProperties props = context.getBean(FileKitProperties.class);
                    assertThat(props.getMaxPresignedExpiration()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    void securityAndMetricsDefaults_areSafe() {
        FileKitProperties props = new FileKitProperties();
        assertThat(props.isEncryptionRequired()).isFalse();
        assertThat(props.isMetricsIncludeBucket()).isFalse();
    }

    @Test
    void securityAndMetricsOptions_bindFromConfig() {
        FileKitProperties props = new FileKitProperties();
        props.setEncryptionRequired(true);
        props.setMetricsIncludeBucket(true);
        assertThat(props.isEncryptionRequired()).isTrue();
        assertThat(props.isMetricsIncludeBucket()).isTrue();
    }
}
