# Using Without Spring Boot

`kit-core` is pure Java — use the SPIs and utilities directly, no Spring dependency.

## File validation

```java
MediaTypeDetector detector = new DefaultMediaTypeDetector();
FileValidationHelper helper = new FileValidationHelper(detector);

// Wrap your file as a FileSource
FileSource source = new FileSource() {
    public String getOriginalFilename() { return "photo.jpg"; }
    public InputStream getInputStream() { return ...; }
    public long getSize() { return 1024; }
    public boolean isEmpty() { return false; }
};

boolean validType = helper.isValidMediaType(source, allowedTypes);
boolean validName = helper.isValidFilename(source);
```

Or use `@ValidFile` with Jakarta Validation and `FileSourceValidator`.

## Standalone image / PDF / archive utilities

All processing SPIs have default implementations — instantiate directly:

```java
ImageMetadataExtractor metadataExtractor = new ImageIOMetadataExtractor();
ImageResizer resizer = new ImageIOResizer();
ImageWatermarker watermarker = new ImageIOWatermarker();
ImageRotator rotator = new ImageIORotator();
ImageCropper cropper = new ImageIOCropper();
ThumbnailGenerator thumbnailGenerator = new DefaultThumbnailGenerator(resizer);
PdfMetadataExtractor pdfExtractor = new PdfBoxMetadataExtractor();
ExifStripper exifStripper = new ImageIOExifStripper();
ImageFormatConverter formatConverter = new ImageIOFormatConverter();
ArchiveMetadataExtractor archiveExtractor = new ZipArchiveMetadataExtractor();
FileEncryptor encryptor = new NoOpFileEncryptor();  // or your custom implementation
```

## Async adapters

`CompletableFuture`-based wrappers around each sync service. On JDK 21+ inject a virtual-thread executor for efficient blocking I/O:

```java
AsyncFileUploadService asyncUpload = AsyncFileUploadService.builder(syncUpload)
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();

CompletableFuture<FileMetadata> result = asyncUpload.uploadAsync(src, type, bucket);
```

Default executor: `ForkJoinPool.commonPool()`.

Siblings:

- `AsyncFileDownloadService`
- `AsyncFileTransferService` — includes `copyAllParallelAsync` / `moveAllParallelAsync`
- `AsyncFileDeleteService` — includes `deleteAllParallelAsync`
- `AsyncFileRenameService`

The parallel batch variants submit each item individually via `CompletableFuture.allOf`.

### WebFlux bridge

One-liner to Reactor:

```java
Mono.fromFuture(asyncUpload.uploadAsync(src, type, bucket))
    .subscribeOn(Schedulers.boundedElastic());
```

## Signed URLs

HMAC-SHA256 time-limited fragments for local-storage download endpoints. Authorization remains the application's responsibility.

```java
SignedUrlSigner signer = new SignedUrlSigner(secretBytes);

String fragment = signer.sign(fileKey, Instant.now().plus(Duration.ofHours(1)));
// → "exp=...&sig=..."

signer.verify(fileKey, exp, sig);
// throws SignedUrlExpiredException / SignedUrlInvalidSignatureException
```

Constant-time comparison via `MessageDigest.isEqual`. Prefer backend-native pre-signed URLs (S3, etc.) when available — see [download.md#pre-signed-urls](download.md#pre-signed-urls).

## Temp file helper

`TempFileBuffer` (package `io.github.dornol.filekit.io`) — `Closeable` temp-file helper for `try-with-resources` cleanup:

```java
try (TempFileBuffer buf = TempFileBuffer.create("upload-")) {
    Files.copy(inputStream, buf.path(), StandardCopyOption.REPLACE_EXISTING);
    // use buf.path()
}
// file deleted here
```

`release()` transfers ownership (skips the auto-delete):

```java
Path kept;
try (TempFileBuffer buf = TempFileBuffer.create("upload-")) {
    Files.copy(inputStream, buf.path(), StandardCopyOption.REPLACE_EXISTING);
    kept = buf.release();  // survives try-with-resources
}
// caller now owns `kept`
```

## Checksum algorithms

```java
ChecksumCalculator md5 = new MessageDigestChecksumCalculator(ChecksumAlgorithm.MD5);
ChecksumCalculator sha256 = new MessageDigestChecksumCalculator(ChecksumAlgorithm.SHA_256);
```

`Sha256ChecksumCalculator` is retained as a no-arg convenience subclass (default).

## Running the example app

The `example` module is a full Spring Boot app demonstrating all SPI implementations:

| SPI | Example impl | Notes |
|---|---|---|
| `FileStorage` | `LocalFileStorage`, `S3FileStorage` | Both registered — dual storage |
| `FileMetadataRepository` | `FileMetadataRepositoryAdapter` | JPA/PostgreSQL |
| `FileFormatExtractor` | `TikaFileFormatExtractor` | Apache Tika |

```bash
cd example
docker compose up -d   # PostgreSQL + MinIO
./gradlew :example:bootRun
```

Open `http://localhost:8880` — upload to LOCAL or S3, view list, download, delete.
MinIO console: `http://localhost:9001` (minioadmin / minioadmin).

### Example endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/upload` | POST | Upload a file (multipart) |
| `/upload-multiple` | POST | Batch upload |
| `/files` | GET | List all uploaded files |
| `/files/{fileKey}/download` | GET | Download |
| `/files/{fileKey}/stream` | GET | Stream with Range header support |
| `/files/{fileKey}/presigned-url` | GET | Pre-signed URL |
| `/files/{fileKey}/uri` | GET | Resolve file URI |
| `/files/{fileKey}/exists` | GET | Existence check |
| `/files/{fileKey}/rename` | PUT | Rename |
| `/files/{fileKey}` | DELETE | Delete |
| `/files/{fileKey}/copy` | POST | Copy |
| `/files/{fileKey}/move` | POST | Move |
| `/files/batch` | DELETE | Batch delete |
| `/files/batch-copy` | POST | Batch copy |
| `/files/batch-move` | POST | Batch move |
| `/image/metadata` | POST | Image metadata |
| `/image/resize` | POST | Resize |
| `/image/thumbnail` | POST | Thumbnail |
| `/image/watermark` | POST | Text watermark |
| `/image/strip-exif` | POST | Strip EXIF |
| `/image/convert` | POST | Format conversion |
| `/pdf/metadata` | POST | PDF metadata |
| `/archive/metadata` | POST | ZIP archive metadata |

## Related

- [storage-spi.md](storage-spi.md) — `FileStorage` SPI contract.
- [validation-and-errors.md](validation-and-errors.md) — `FileValidationHelper` reference.
- [image-processing.md](image-processing.md) — image operations.
