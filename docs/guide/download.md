# Upload / Download / Delete services

The core service-level flow. `FileUploadService`, `FileDownloadService`, and `FileDeleteService` coordinate validation, storage, metadata, events, and (optionally) virus scanning, quota, and encryption.

## When to use

- You have a `MultipartFile` or `FileSource` and want to persist it through the SPI layer.
- You need HTTP download responses with `Content-Disposition`, range support, or inline preview.
- You want transactional upload (delete file on callback failure).

## Upload flow

1. Validate the reported file size against `max-upload-size` config.
2. Validate filename (length ≤ 200, no `..` / `/` / `\`).
3. Copy content to a temp file while enforcing the actual byte count against `max-upload-size`.
4. Virus scan if a `VirusScanner` bean is registered — reject if `INFECTED` or `ERROR`.
5. Compute streaming checksum; if a file with the same checksum exists, return the existing metadata (dedup — **no quota consumed, no `onUploaded` event fired**).
6. Quota check (if `QuotaChecker` configured) — reject with `QUOTA_EXCEEDED`.
7. Detect file format (MIME, extension).
8. Delegate to `FileStorage.upload()` with an `InputStream`.
9. Run callback (if provided) — on failure: delete from storage and throw.
10. Save `FileMetadata` and fire `onUploaded`.
11. Clean up temp file (in `finally`).

## Download flow

1. Look up `FileMetadata` by file key.
2. Resolve the `FileStorage` from `metadata.location().storageType()`.
3. Load content and fire `onDownloaded`.

## Delete flow

1. Look up `FileMetadata` by file key.
2. Resolve the `FileStorage` and delete bytes.
3. Delete the metadata record and fire `onDeleted`.

## Transactional upload with callback

Run business logic after upload — on exception, the file is rolled back (deleted from storage):

```java
uploadService.upload(file, StorageType.LOCAL, "uploads", metadata -> {
    businessService.process(metadata);  // throws → file rolled back, onUploadFailed fires
});
```

The `FileEventListener.onUploadFailed(metadata, cause)` event fires after storage cleanup (both callback and save failures). Subscribe instead of catching `CALLBACK_FAILED` manually.

## File deletion

```java
@Autowired FileDeleteService deleteService;

deleteService.delete(fileKey);
// 1. Look up metadata (throws FileStorageException if not found)
// 2. Delete from storage
// 3. Delete metadata record, fire onDeleted
```

## Virus scanning

Register a `VirusScanner` bean and uploads are automatically scanned before storage:

```java
@Component
public class ClamAvVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(byte[] fileBytes) {
        try {
            boolean clean = clamAvClient.scan(fileBytes);
            return clean ? ScanResult.clean() : ScanResult.infected("Threat detected");
        } catch (Exception e) {
            return ScanResult.error("Scan service unavailable: " + e.getMessage());
        }
    }

    // Optional: override for streaming support
    @Override
    public ScanResult scan(InputStream inputStream) {
        // e.g., pipe directly to ClamAV INSTREAM command
    }
}
```

| Status | Behavior |
|---|---|
| `CLEAN` | Upload proceeds |
| `INFECTED` | Reject with `FileStorageException(VIRUS_DETECTED)` |
| `ERROR` | Reject with `FileStorageException(VIRUS_SCAN_ERROR)` — **fail-closed** default |

For **fail-open**: return `ScanResult.clean()` from your error handling. If no `VirusScanner` bean is registered, scanning is skipped entirely.

### Memory note

The `VirusScanner.scan(InputStream)` default reads the full stream into memory and delegates to `scan(byte[])`. Override it for large or untrusted uploads, for example by piping the stream directly to ClamAV INSTREAM or another streaming scanner protocol.

## Pre-signed URLs

Time-limited direct-access URLs for storage backends that support them (S3, etc.):

```java
String url = downloadService.generatePresignedUrl(fileKey, Duration.ofHours(1));
```

The `FileStorage` default throws `UnsupportedOperationException` — implement `generatePresignedUrl()` in your storage (see [s3-storage.md](s3-storage.md)).

Max lifetime: `file-kit.max-presigned-expiration` (e.g. `24h`). Exceeding the limit throws `FileStorageException(PRESIGNED_URL_FAILED)`.

For **local-storage** backends without native pre-signing, see [standalone.md](standalone.md#signed-urls) for HMAC-based `SignedUrlSigner`.

## Range request (HTTP 206)

`FileResponseBuilder` supports RFC 7233 byte-range requests for streaming and resumable downloads:

```java
@GetMapping("/files/{fileKey}/stream")
public ResponseEntity<Resource> stream(
        @PathVariable String fileKey,
        @RequestHeader(value = "Range", required = false) String rangeHeader) {
    DownloadResult result = downloadService.download(fileKey);
    return FileResponseBuilder.inline(result.metadata())
            .range(rangeHeader)
            .body(new InputStreamResource(result.content()));
}
```

| Range header | Response |
|---|---|
| Absent | `200 OK` + `Accept-Ranges: bytes` |
| Valid range | `206 Partial Content` + `Content-Range` |
| Invalid range | `416 Range Not Satisfiable` |

`FileStorage` provides a default `loadRange(metadata, start, end)` that skips to the start offset and wraps the stream in `BoundedInputStream`.

## `FileResponseBuilder`

Utility for building download/inline HTTP responses with correct `Content-Disposition` (RFC 5987, Korean filename support) and range handling:

```java
// Download
return FileResponseBuilder.download(metadata).body(resource);

// Inline preview with cache
return FileResponseBuilder.inline(metadata)
        .cache(Duration.ofHours(1))
        .body(resource);

// Custom filename + content type
return FileResponseBuilder.download("report.xlsx")
        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(resource);

// Range request
return FileResponseBuilder.inline(metadata)
        .range(request.getHeader("Range"))
        .body(resource);
```

## Controller example (Spring MVC)

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;
    private final FileDownloadService downloadService;
    private final FileDeleteService deleteService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file) throws IOException {
        FileMetadata metadata = uploadService.upload(
                new MultipartFileSource(file), StorageType.LOCAL, "my-bucket");
        return ResponseEntity.ok(Map.of("fileKey", metadata.key()));
    }

    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileKey) {
        DownloadResult result = downloadService.download(fileKey);
        return FileResponseBuilder.download(result.metadata())
                .body(new InputStreamResource(result.content()));
    }

    @DeleteMapping("/files/{fileKey}")
    public ResponseEntity<?> delete(@PathVariable String fileKey) {
        deleteService.delete(fileKey);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/files/{fileKey}/presigned-url")
    public ResponseEntity<?> presignedUrl(@PathVariable String fileKey) {
        String url = downloadService.generatePresignedUrl(fileKey, Duration.ofHours(1));
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/files/{fileKey}/stream")
    public ResponseEntity<Resource> stream(
            @PathVariable String fileKey,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        DownloadResult result = downloadService.download(fileKey);
        return FileResponseBuilder.inline(result.metadata())
                .range(rangeHeader)
                .body(new InputStreamResource(result.content()));
    }
}
```

## Controller example (Spring WebFlux)

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;

    @PostMapping("/upload")
    public Mono<Map<String, String>> upload(@RequestPart("file") FilePart filePart) {
        return FilePartSource.from(filePart)
            .flatMap(source -> Mono.fromCallable(() -> {
                try (source) {
                    FileMetadata metadata = uploadService.upload(
                        source, StorageType.LOCAL, "my-bucket");
                    return Map.of("fileKey", metadata.key());
                }
            }).subscribeOn(Schedulers.boundedElastic()));
    }
}
```

## Streaming checksum verification

Set `file-kit.verify-checksum-on-download: true` to verify content integrity on download. Uses `ChecksumVerifyingInputStream` — memory footprint is O(buffer), not O(file). Throws `FileStorageException(CHECKSUM_MISMATCH)` on mismatch.

## Related

- [storage-spi.md](storage-spi.md) — SPI contract.
- [file-upload.md](file-upload.md) — `@ValidMultipartFile` annotation.
- [quota.md](quota.md) — quota enforcement.
- [encryption.md](encryption.md) — at-rest encryption.
- [lifecycle-events.md](lifecycle-events.md) — event listeners.
- [batch-operations.md](batch-operations.md) — batch upload/delete, copy/move.
