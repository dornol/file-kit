package io.github.dornol.filekit.test;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link FileMetadataRepository} for testing.
 */
public class InMemoryMetadataRepository implements FileMetadataRepository {

    private final Map<String, FileMetadata> byKey = new ConcurrentHashMap<>();
    private final Map<String, FileMetadata> byChecksum = new ConcurrentHashMap<>();

    @Override
    public FileMetadata findByChecksum(String checksum) {
        return byChecksum.get(checksum);
    }

    @Override
    public FileMetadata findByKey(String key) {
        return byKey.get(key);
    }

    @Override
    public FileMetadata save(FileMetadata metadata) {
        byKey.put(metadata.key(), metadata);
        byChecksum.put(metadata.checksum(), metadata);
        return metadata;
    }

    @Override
    public void deleteByKey(String key) {
        FileMetadata removed = byKey.remove(key);
        if (removed != null) {
            byChecksum.remove(removed.checksum());
        }
    }

    public int count() {
        return byKey.size();
    }
}
