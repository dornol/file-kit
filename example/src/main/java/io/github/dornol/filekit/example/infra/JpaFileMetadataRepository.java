package io.github.dornol.filekit.example.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaFileMetadataRepository extends JpaRepository<FileMetadataEntity, String> {

    Optional<FileMetadataEntity> findByChecksum(String checksum);

}
