# File Upload (`@ValidMultipartFile`)

Validate uploaded files declaratively with Jakarta Validation.

## When to use

- Spring MVC controllers receiving `MultipartFile` — add the annotation to the parameter.
- Image uploads where width/height constraints matter.
- WebFlux + `FilePart` — annotation not available; see [WebFlux section](#webflux-filepart-support) below.

## Setup

### 1. Define allowed media types

Implement `SafeMediaType` on an enum:

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

    @Override public String getMediaType() { return mediaType; }
    @Override public Set<String> getExtensions() { return extensions; }
}
```

### 2. Annotate the controller parameter

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

No configuration class required.

## Image dimension validation

Enforce min/max width and height on image uploads:

```java
@PostMapping("/upload-avatar")
public ResponseEntity<?> uploadAvatar(
        @RequestParam("file")
        @ValidMultipartFile(
                value = AllowedMediaType.class,
                maxSize = 5 * 1024 * 1024,
                minWidth = 200, minHeight = 200,
                maxWidth = 4096, maxHeight = 4096)
        MultipartFile file) {
    return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
}
```

Dimension checks use ImageIO to read only the image header — no full decode, minimal overhead. Non-image files fail validation when any dimension constraint is set.

## WebFlux (FilePart) support

`@ValidMultipartFile` does not work for `FilePart` because `FilePart.content()` is a single-use `Flux<DataBuffer>` — consuming it in a validator would prevent the controller from reading the file.

Use `FilePartSource` to adapt a `FilePart` to `FileSource`, then validate via `FileUploadService` (size, filename, virus scan checks):

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
                        source, StorageType.LOCAL, "bucket");
                    return Map.of("fileKey", metadata.key());
                }
            }).subscribeOn(Schedulers.boundedElastic()));
    }
}
```

**How it works:**
- `FilePartSource.from(FilePart)` buffers the reactive content to a temp file via `transferTo(Path)`.
- The resulting `FilePartSource` implements `FileSource`, so it works with `FileUploadService`, `FileValidationHelper`, and all validation logic.
- `getInputStream()` is replayable (multiple reads), unlike `FilePart.content()`.
- `Closeable` — always use try-with-resources to clean up the temp file.
- Use `Schedulers.boundedElastic()` for the blocking upload call inside a reactive pipeline.

## Configuration

`application.yml`:

```yaml
file-kit:
  max-upload-size: 10485760              # 10MB, 0 = unlimited (default)
  verify-checksum-on-download: false     # verify integrity on download (default: false)
  max-presigned-expiration: 24h          # maximum pre-signed URL lifetime (default: no limit)
```

## Validation rules

See [validation-and-errors.md](validation-and-errors.md) for the full check matrix and i18n message keys.

## Related

- [upload/download service flow](download.md) — full service-level upload flow including virus scan, checksum dedup, quota, storage delegation.
- [validation-and-errors.md](validation-and-errors.md) — check descriptions and message keys.
- [image-processing.md](image-processing.md) — post-upload image manipulation.
