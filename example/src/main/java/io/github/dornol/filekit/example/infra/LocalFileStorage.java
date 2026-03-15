package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.storage.local.ObjectKeyStrategy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Registers a {@link io.github.dornol.filekit.storage.local.LocalFileStorage}
 * with hash-prefixed directory layout as a Spring bean.
 */
@Component
public class LocalFileStorage extends io.github.dornol.filekit.storage.local.LocalFileStorage {

    public LocalFileStorage() {
        super(
                Path.of(System.getProperty("java.io.tmpdir"), "file-kit-example"),
                StorageType.LOCAL,
                ObjectKeyStrategy.hashPrefixed(2)
        );
    }

}
