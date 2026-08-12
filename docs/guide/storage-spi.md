# Storage SPI (`FileStorage`)

file-kit routes all persistence through the `FileStorage` SPI. Register a `FileStorage` bean and the upload/download/delete services are auto-wired.

## What you need to provide

| Interface | Purpose | Default |
|---|---|---|
| `ChecksumCalculator` | Content checksum for deduplication | `Sha256ChecksumCalculator` (auto-registered) |
| `FileFormatExtractor` | Detect MIME + extension from content | **You provide** |
| `FileMetadataRepository` | Persist + query file metadata | **You provide** |
| `FileStorage` | Store + load file bytes | Built-in: `LocalFileStorage`, `InMemoryFileStorage` |
| `QuotaPolicy` / `QuotaUsageProvider` | Quota enforcement | Optional — both required together |
| `FileEventListener` | Lifecycle events | Optional |

## Minimal setup

`ChecksumCalculator` is auto-registered (SHA-256). You only need `FileFormatExtractor`, `FileMetadataRepository`, and at least one `FileStorage`:

```java
@Component
public class MyFileFormatExtractor implements FileFormatExtractor {
    public FileFormat extract(InputStream inputStream) { /* detect MIME & extension */ }
}

@Component
public class MyFileMetadataRepository implements FileMetadataRepository {
    public FileMetadata findByChecksum(String checksum) { /* query DB */ }
    public FileMetadata findByKey(String key) { /* query DB */ }
    public FileMetadata save(FileMetadata metadata) { /* insert DB */ }
    public void deleteByKey(String key) { /* delete from DB */ }
    // getByKey(key) is a default method — throws FileStorageException if not found
    // existsByKey(key) is a default method
}
```

## Built-in implementations

## Checksum streaming

The starter auto-registers `Sha256ChecksumCalculator`, which is backed by
`MessageDigestChecksumCalculator` and supports incremental checksum computation.
This keeps upload checksum work O(buffer).

If you provide your own `ChecksumCalculator`, override
`newComputation()` for large or untrusted files. The interface default returns a
fallback that buffers all updates in memory before calling `checksum(byte[])`;
that is convenient for small test fixtures, but not appropriate for production
large-file paths.

```java
public final class StreamingChecksumCalculator implements ChecksumCalculator {

    @Override
    public String checksum(byte[] bytes) {
        return new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256)
                .checksum(bytes);
    }

    @Override
    public ChecksumComputation newComputation() {
        return new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256)
                .newComputation();
    }
}
```

### `LocalFileStorage`

Stores files on the local filesystem with a configurable directory layout:

```java
public enum StorageType { LOCAL }

@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(Path.of("/data/files"), StorageType.LOCAL);
}
```

Directory layout is controlled by `ObjectKeyStrategy`:

| Strategy | Example path | Use case |
|---|---|---|
| `ObjectKeyStrategy.flat()` | `key.png` | Simple, small-scale |
| `ObjectKeyStrategy.dateBased()` | `2026/03/15/key.png` | Date-based organization |
| `ObjectKeyStrategy.hashPrefixed(2)` | `73/3e/key.png` | Large-scale, avoids FS bottlenecks |

```java
@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(
            Path.of("/data/files"),
            StorageType.LOCAL,
            ObjectKeyStrategy.hashPrefixed(2));
}
```

### `InMemoryFileStorage`

In-memory storage — for testing and prototyping:

```java
@Bean
public FileStorage memoryStorage() {
    return new InMemoryFileStorage(StorageType.LOCAL);
}
```

Thread-safe (backed by `ConcurrentHashMap`), but **not recommended for production** — no size limits, all data lost on restart.

## Custom storage

Implement `FileStorage` to integrate with any backend. Five methods:

| Method | Purpose |
|---|---|
| `getStorageType()` | Return your `StorageType` enum value |
| `upload(FileUploadCommand)` | Store bytes from `command.content()` `InputStream`, return `FileLocation` |
| `load(FileMetadata)` | Return an `InputStream` of file bytes |
| `delete(FileMetadata)` | Remove bytes |
| `resolveUri(FileMetadata)` | Build a URI for direct access (optional, can be your download endpoint) |
| `generatePresignedUrl(FileMetadata, Duration)` | Time-limited direct URL (throws `UnsupportedOperationException` by default) |

Errors should be wrapped in `FileStorageException` with appropriate message keys (`UPLOAD_FAILED`, `DOWNLOAD_FAILED`, `DELETE_FAILED`, `PRESIGNED_URL_FAILED`).

Full example: [s3-storage.md](s3-storage.md).

### `loadRange` default

`FileStorage` provides a default `loadRange(metadata, start, end)` that skips to the start offset and wraps the stream with `BoundedInputStream` for efficient partial reads — override for backends that support native range queries (e.g. S3 `Range` header).

## Multiple storage backends

Register multiple `FileStorage` beans — `FileStorageResolver` routes by `storageType`. Each `storageType` must be unique; duplicates throw `IllegalArgumentException` at startup.

## Optional health checks

Implement `StorageHealthCheck` alongside `FileStorage` when the backend supports an active availability probe:

```java
public final class MyStorage implements FileStorage, StorageHealthCheck {
    @Override
    public void check() {
        // Probe the configured backend and throw RuntimeException when unavailable.
    }
}
```

With the Spring Boot starter and Actuator, these probes are exposed through the standard health endpoint. Storages that do not implement the SPI remain supported and are reported as passive checks. File-kit does not perform automatic orphan cleanup because deletion requires application-specific metadata lifecycle and object-listing policies.

```java
public enum StorageType { LOCAL, S3 }

@Bean
public FileStorage localStorage() {
    return new LocalFileStorage(Path.of("/data/files"), StorageType.LOCAL);
}

@Bean
public FileStorage s3Storage() {
    return new S3FileStorage(s3Client);
}
```

Upload to a specific storage:

```java
uploadService.upload(file, StorageType.LOCAL, "uploads");
uploadService.upload(file, StorageType.S3, "my-s3-bucket");
```

Download and delete are automatic — `FileMetadata` records which storage was used:

```java
downloadService.download(fileKey);  // reads from the recorded storage
deleteService.delete(fileKey);      // deletes from the recorded storage
```

## Related

- [s3-storage.md](s3-storage.md) — full S3 reference implementation.
- [download.md](download.md) — service-level upload/download/delete flow, pre-signed URLs, range requests.
- [quota.md](quota.md) — quota enforcement SPI.
- [encryption.md](encryption.md) — `FileEncryptor` SPI.
