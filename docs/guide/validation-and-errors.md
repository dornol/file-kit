# Validation and Error Messages

Validation check reference + i18n message keys for both validation and storage errors.

## Validation checks

| Check | Description |
|---|---|
| Media type | Detects actual MIME type and compares against allowed set |
| File empty | Rejects zero-byte files |
| File size | Rejects files exceeding `maxSize` (bytes) |
| Filename | Rejects null, blank, too-long (>200 chars), path traversal (`..`, `/`, `\`) |
| Extension | Verifies the file extension matches the detected content type |
| Image dimensions | Validates width/height against `minWidth`/`maxWidth`/`minHeight`/`maxHeight` (only when set) |

Validation helpers: `FileValidationHelper` (facade), `MediaTypeValidator`, `ImageDimensionValidator`. The latter two are public final — depend on just the one you need.

## Validation message keys

Jakarta Validation standard interpolation. Add `ValidationMessages.properties` to your classpath:

```properties
file-kit.validation.unsupported-media-type=Unsupported file type
file-kit.validation.file-empty=File is empty
file-kit.validation.file-too-large=File size exceeded
file-kit.validation.invalid-filename=Invalid filename
file-kit.validation.invalid-extension=Invalid file extension
file-kit.validation.image-not-readable=File is not a valid image
file-kit.validation.image-width-too-small=Image width is too small
file-kit.validation.image-width-too-large=Image width is too large
file-kit.validation.image-height-too-small=Image height is too small
file-kit.validation.image-height-too-large=Image height is too large
```

## Storage error message keys

`FileStorageException` exposes `getMessageKey()` for i18n lookup. Complete key list:

```properties
file-kit.storage.file-not-found=File not found
file-kit.storage.storage-not-found=Unregistered storage type
file-kit.storage.upload-failed=File upload failed
file-kit.storage.download-failed=File download failed
file-kit.storage.delete-failed=File deletion failed
file-kit.storage.callback-failed=Post-upload processing failed, file has been deleted
file-kit.storage.file-too-large=File size exceeds the maximum allowed
file-kit.storage.invalid-filename=Invalid filename
file-kit.storage.virus-detected=Virus detected in uploaded file
file-kit.storage.virus-scan-error=Virus scan failed
file-kit.storage.presigned-url-failed=Pre-signed URL generation failed
file-kit.storage.range-not-satisfiable=Invalid byte range requested
file-kit.image.processing-failed=Image processing failed
file-kit.pdf.processing-failed=PDF processing failed
file-kit.archive.processing-failed=Archive processing failed
file-kit.storage.copy-failed=File copy failed
file-kit.storage.move-failed=File move failed
file-kit.storage.encryption-failed=File encryption failed
file-kit.storage.decryption-failed=File decryption failed
file-kit.storage.quota-exceeded=Storage quota exceeded
file-kit.storage.checksum-mismatch=Downloaded content checksum does not match stored checksum
```

```java
try {
    downloadService.download(key);
} catch (FileStorageException e) {
    String localized = messageSource.getMessage(e.getMessageKey(), null, locale);
    // ...
}
```

## Media type detection

The starter auto-registers a `MediaTypeDetector` with this priority:

| Priority | Condition | Detector |
|---|---|---|
| 1 | User-defined `MediaTypeDetector` bean | Your implementation |
| 2 | Apache Tika on classpath | `TikaMediaTypeDetector` |
| 3 | Fallback | `DefaultMediaTypeDetector` (Java `URLConnection` + magic-byte sniffing) |

For production, Apache Tika is recommended for accurate detection.

`DefaultMediaTypeDetector` runs a magic-byte sniff **before** the JDK probes. Covers: PDF, ZIP / DOCX / XLSX / PPTX / APK, PNG, JPEG, GIF, BMP, WebP, MP4, OGG, Zstandard. No extra dependencies.

## Related

- [file-upload.md](file-upload.md) — `@ValidMultipartFile` annotation.
- [download.md](download.md) — upload flow (validation step ordering).
- [standalone.md](standalone.md) — using `FileValidationHelper` without Spring.
