package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.spi.FileMetadataRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileMetadataRepositoryAdapter implements FileMetadataRepository {

    private final JpaFileMetadataRepository jpaRepository;

    public FileMetadataRepositoryAdapter(JpaFileMetadataRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FileMetadata findByChecksum(String checksum) {
        return jpaRepository.findByChecksum(checksum)
                .map(FileMetadataEntity::toDomain)
                .orElse(null);
    }

    @Override
    public FileMetadata findByKey(String key) {
        return jpaRepository.findById(key)
                .map(FileMetadataEntity::toDomain)
                .orElse(null);
    }

    @Override
    public FileMetadata save(FileMetadata metadata) {
        FileMetadataEntity entity = FileMetadataEntity.from(metadata);
        jpaRepository.save(entity);
        return metadata;
    }

    public List<FileMetadata> findAll() {
        return jpaRepository.findAll().stream()
                .map(FileMetadataEntity::toDomain)
                .toList();
    }

}
