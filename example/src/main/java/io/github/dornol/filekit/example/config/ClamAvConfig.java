package io.github.dornol.filekit.example.config;

import io.github.dornol.filekit.scan.ClamAvVirusScanner;
import io.github.dornol.filekit.scan.VirusScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the streaming ClamAV scanner when explicitly enabled. */
@Configuration
@ConditionalOnProperty(prefix = "app.clamav", name = "enabled", havingValue = "true")
public class ClamAvConfig {

    @Bean
    public VirusScanner clamAvVirusScanner(
            @Value("${app.clamav.host:localhost}") String host,
            @Value("${app.clamav.port:3310}") int port,
            @Value("${app.clamav.connect-timeout:5s}") String connectTimeout,
            @Value("${app.clamav.read-timeout:30s}") String readTimeout) {
        return new ClamAvVirusScanner(host, port,
                DurationStyle.detectAndParse(connectTimeout),
                DurationStyle.detectAndParse(readTimeout));
    }
}
