# file-kit Guides

Topic-focused references. Each page = one concern.

## Spring Boot usage

- [file-upload.md](file-upload.md) — `@ValidMultipartFile` annotation, image dimension checks, WebFlux `FilePart` support, `application.yml` config.
- [download.md](download.md) — `FileUploadService` / `FileDownloadService` / `FileDeleteService` flow, virus scanning, transactional callback, range requests (HTTP 206), pre-signed URLs, `FileResponseBuilder`, MVC + WebFlux controller examples.
- [batch-operations.md](batch-operations.md) — batch upload / delete, file copy / move (single + batch), ZIP archive listing, file existence, rename.

## Storage

- [storage-spi.md](storage-spi.md) — `FileStorage` SPI contract, built-in `LocalFileStorage` / `InMemoryFileStorage`, `ObjectKeyStrategy`, multi-backend routing.
- [s3-storage.md](s3-storage.md) — full S3 / MinIO / R2 / Wasabi reference implementation.

## Cross-cutting concerns

- [quota.md](quota.md) — `QuotaPolicy` + `QuotaUsageProvider` SPI, upload / copy / move enforcement.
- [encryption.md](encryption.md) — `FileEncryptor` SPI, at-rest encryption, dedup preservation.
- [lifecycle-events.md](lifecycle-events.md) — `FileEventListener` events (upload / download / delete / copy / move / rename + `onUploadFailed`).
- [validation-and-errors.md](validation-and-errors.md) — validation check matrix, i18n message keys (validation + storage).

## Media processing

- [image-processing.md](image-processing.md) — metadata, resize, thumbnail, watermark, rotate, crop, EXIF strip, format convert.
- [pdf-metadata.md](pdf-metadata.md) — PDF metadata via Apache PDFBox.

## Non-Spring usage

- [standalone.md](standalone.md) — pure `kit-core` usage, async adapters (`CompletableFuture`, virtual threads), `SignedUrlSigner`, `TempFileBuffer`, running the `example` app, endpoint list.
