package io.github.dornol.filekit.example.config;

import io.github.dornol.filekit.scan.ClamAvVirusScanner;
import io.github.dornol.filekit.scan.VirusScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ClamAvConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClamAvConfig.class));

    @Test
    void disabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(VirusScanner.class));
    }

    @Test
    void enabled_registersStreamingScanner() {
        contextRunner
                .withPropertyValues(
                        "app.clamav.enabled=true",
                        "app.clamav.host=clamav",
                        "app.clamav.port=3310",
                        "app.clamav.connect-timeout=1s",
                        "app.clamav.read-timeout=2s")
                .run(context -> {
                    assertThat(context).hasSingleBean(VirusScanner.class);
                    assertThat(context.getBean(VirusScanner.class))
                            .isInstanceOf(ClamAvVirusScanner.class);
                });
    }
}
