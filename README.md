# file-kit

A lightweight Java library for file validation, upload, and download. Validates uploaded files by media type, file size, filename safety, and extension-content consistency. Provides a pluggable storage abstraction for uploading and downloading files with checksum-based deduplication.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `kit-core` | `io.github.dornol:file-kit-core` | Pure Java validation, upload/download logic. No framework dependency. |
| `kit-spring-boot-starter` | `io.github.dornol:file-kit-spring-boot-starter` | Spring Boot auto-configuration with `@ValidMultipartFile` and storage integration. |

## Quick Start (Spring Boot)

### 1. Add dependency

```groovy
// Gradle
implementation 'io.github.dornol:file-kit-spring-boot-starter:0.0.1'

// Optional: for better MIME detection
implementation 'org.apache.tika:tika-core:3.1.0'
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.dornol</groupId>
    <artifactId>file-kit-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

### 2. Define allowed media types

```java
public enum AllowedMediaType implements SafeMediaType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    PDF("application/pdf", Set.of("pdf"));

    private final String mediaType;
    private final Set<String> extensions;

    AllowedMediaType(String mediaType, Set<String> extensions) {
        this.mediaType = mediaType;
        this.extensions = extensions;
    }

    @Override
    public String getMediaType() { return mediaType; }

    @Override
    public Set<String> getExtensions() { return extensions; }
}
```

### 3. Use the annotation

```java
@RestController
@Validated
public class FileUploadController {

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file")
            @ValidMultipartFile(value = AllowedMediaType.class, maxSize = 10 * 1024 * 1024)
            MultipartFile file) {
        // file is validated - safe to process
        return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
    }
}
```

That's it. No configuration class needed.

## File Storage

file-kit provides a pluggable storage abstraction. Implement the SPI interfaces and a `FileStorage`, and the upload/download flow is auto-configured.

### 1. Define a storage type

```java
public enum StorageType { LOCAL, S3 }
```

### 2. Implement the SPI interfaces

```java
@Component
public class MyChecksumCalculator implements ChecksumCalculator {
    public String checksum(byte[] bytes) { /* SHA-256, MD5, etc. */ }
}

@Component
public class MyFileFormatExtractor implements FileFormatExtractor {
    public FileFormat extract(InputStream inputStream) { /* detect MIME & extension */ }
}

@Component
public class MyFileMetadataRepository implements FileMetadataRepository {
    public FileMetadata findByChecksum(String checksum) { /* query DB */ }
    public FileMetadata findByKey(String key) { /* query DB */ }
    public FileMetadata save(FileMetadata metadata) { /* insert DB */ }
}
```

### 3. Implement FileStorage

```java
@Component
public class LocalFileStorage implements FileStorage {
    public Enum<?> getStorageType() { return StorageType.LOCAL; }
    public FileLocation upload(FileUploadCommand command) { /* write to disk */ }
    public InputStream load(FileMetadata metadata) { /* read from disk */ }
    public String resolveUri(FileMetadata metadata) { /* return download URL */ }
}
```

### 4. Use the auto-configured services

```java
@RestController
public class FileController {

    private final FileUploadService uploadService;
    private final FileDownloadService downloadService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file) throws IOException {
        FileMetadata metadata = uploadService.upload(
                new MultipartFileSource(file), StorageType.LOCAL, "my-bucket");
        return ResponseEntity.ok(Map.of("fileKey", metadata.key()));
    }

    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileKey) {
        DownloadResult result = downloadService.download(fileKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.metadata().format().mimeType()))
                .body(new InputStreamResource(result.content()));
    }
}
```

### Upload flow

1. Read file bytes, compute checksum
2. If a file with the same checksum already exists, return the existing metadata (deduplication)
3. Detect file format (MIME type, extension)
4. Delegate to `FileStorage.upload()` for physical storage
5. Save and return `FileMetadata`

### Auto-configured beans

The following beans are registered automatically when their dependencies are present:

| Bean | Condition |
|------|-----------|
| `FileStorageResolver` | At least one `FileStorage` bean |
| `FileUploadService` | `ChecksumCalculator` + `FileMetadataRepository` + `FileFormatExtractor` + `FileStorageResolver` |
| `FileDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |
| `SpringDownloadService` | `FileMetadataRepository` + `FileStorageResolver` |

## Validation Checks

| Check | Description |
|-------|-------------|
| Media type | Detects actual MIME type and compares against allowed set |
| File empty | Rejects zero-byte files |
| File size | Rejects files exceeding `maxSize` (in bytes) |
| Filename | Rejects null, blank, too-long (>200 chars), and path traversal patterns |
| Extension | Verifies that file extension matches the detected content type |

## Custom Validation Messages

file-kit uses Jakarta Validation's standard message interpolation. Add a `ValidationMessages.properties` to your classpath:

```properties
file-kit.validation.unsupported-media-type=Unsupported file type
file-kit.validation.file-empty=File is empty
file-kit.validation.file-too-large=File size exceeded
file-kit.validation.invalid-filename=Invalid filename
file-kit.validation.invalid-extension=Invalid file extension
```

## Media Type Detection

The starter auto-registers a `MediaTypeDetector` with the following priority:

| Priority | Condition | Detector |
|----------|-----------|----------|
| 1 | User-defined `MediaTypeDetector` bean | Your implementation |
| 2 | Apache Tika on classpath | `TikaMediaTypeDetector` |
| 3 | Fallback | `DefaultMediaTypeDetector` (Java `URLConnection`-based) |

For production use, Apache Tika is recommended for accurate detection.

## Using without Spring Boot

Use `kit-core` directly with the `FileSource` abstraction:

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

// Use helper methods directly
boolean validType = helper.isValidMediaType(source, allowedTypes);
boolean validName = helper.isValidFilename(source);
```

Or use `@ValidFile` with Jakarta Validation and `FileSourceValidator`.

## Requirements

- Java 17+
- Jakarta Validation 3.x (for annotation-based validation)
- Spring Boot 3+ / 4+ (for the starter module)

## License

[MIT](LICENSE)
