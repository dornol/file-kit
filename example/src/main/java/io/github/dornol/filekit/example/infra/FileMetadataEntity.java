package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.StorageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_metadata")
public class FileMetadataEntity {

    @Id
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, unique = true)
    private String checksum;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private String extension;

    @Column(nullable = false)
    private String primaryType;

    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageType storageType;

    protected FileMetadataEntity() {}

    public static FileMetadataEntity from(FileMetadata metadata) {
        FileMetadataEntity entity = new FileMetadataEntity();
        entity.key = metadata.key();
        entity.name = metadata.name();
        entity.size = metadata.size();
        entity.checksum = metadata.checksum();
        entity.mimeType = metadata.format().mimeType();
        entity.extension = metadata.format().extension();
        entity.primaryType = metadata.format().primaryType();
        entity.bucket = metadata.location().bucket();
        entity.objectKey = metadata.location().objectKey();
        entity.storageType = (StorageType) metadata.location().storageType();
        return entity;
    }

    public FileMetadata toDomain() {
        return new FileMetadata(
                key, name, size, checksum,
                new FileFormat(mimeType, extension, primaryType),
                new FileLocation(bucket, objectKey, storageType)
        );
    }

}
