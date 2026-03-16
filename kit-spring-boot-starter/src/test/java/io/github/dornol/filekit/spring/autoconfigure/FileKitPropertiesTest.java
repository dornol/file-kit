package io.github.dornol.filekit.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
}
