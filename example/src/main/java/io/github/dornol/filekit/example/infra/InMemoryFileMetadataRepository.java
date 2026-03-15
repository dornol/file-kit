package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryFileMetadataRepository implements FileMetadataRepository {

    private final ConcurrentHashMap<String, FileMetadata> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FileMetadata> byChecksum = new ConcurrentHashMap<>();

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

    public Collection<FileMetadata> findAll() {
        return byKey.values();
    }

}
