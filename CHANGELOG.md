# Changelog

All notable changes to this project are documented in this file.

## [0.1.9] - 2025-07-12

### Added
- Download integrity verification: optional checksum check on download (`Builder.checksumCalculator()`)
- Pre-signed URL expiration limit (`Builder.maxPresignedExpiration()`)
- Spring Boot properties: `verify-checksum-on-download`, `max-presigned-expiration`
- `CHECKSUM_MISMATCH` error key

### Changed
- `FileMetadataRepository.update()` now verifies key exists before saving (throws `FILE_NOT_FOUND` if missing)
- Strengthened Javadoc: dedup race condition guidance, callback quota recovery pattern, move partial failure cleanup

### Tests
- `BatchUploadResultTest`, `BatchTransferResultTest`: validation, convenience methods, defensive copy
- `FileKitPropertiesTest`: new property fields coverage

## [0.1.8] - 2025-07-12

### Added
- `FileMetadataRepository.existsByKey()` default method
- `FileMetadataRepository.update()` default method
- `FileRenameService`: rename files by metadata update without touching storage
- `FileMetadata.withName()`: immutable name copy
- `FileUploadService.uploadAll()` with `BatchUploadResult`
- `FileTransferService.copyAll()` / `moveAll()` with `BatchTransferResult`
- `FileEventListener.onRenamed()` event
- `FileRenameService` auto-configured bean in Spring Boot
- `RELEASE_CHECKLIST.md`

### Changed
- Removed all deprecated constructors from `FileDownloadService` (3) and `SpringDownloadService` (2)
- `FileEventPublisher`: `List.copyOf()` defensive copy for listener list
- Replaced FQN usage with proper imports across 8 files
- Removed unused `ByteArrayInputStream` import from `ArchiveMetadataExtractor`

### Tests
- Comprehensive tests for all new features (rename, batch upload/transfer, exists)
- Strengthened existing tests: concrete assertions, edge cases, event verification

## [0.1.7] - 2025-07-12

### Changed
- Unified Builder pattern across `FileDeleteService` and `FileTransferService` (removed public constructors)
- Migrated all test code from direct constructor calls to Builder pattern

## [0.1.6] - 2025-07-12

### Fixed
- Potential NPE in `ImageIOUtils` when `ImageOutputStream` is null

## [0.1.5] - 2025-07-12

### Added
- Builder pattern for `FileUploadService`, `FileDownloadService`
- `DecryptionHelper`: extracted shared decryption-to-stream logic
- Centralized validation message keys

### Changed
- Extracted duplicated code and removed dead code
- Reduced validator boilerplate with shared base class

## [0.1.4] - 2025-07-11

### Added
- Atomic write in `LocalFileStorage` (temp file + rename)

## [0.1.3] - 2025-07-11

### Fixed
- Security, resource leak, and robustness issues across modules

## [0.1.2] - 2025-07-11

### Changed
- Refactored `FileEventPublisher` and `FileUploadService`

## [0.1.1] - 2025-07-11

### Fixed
- Resource leaks in `loadRange`, decryption, and validation paths

## [0.1.0] - 2025-07-11

### Added
- Storage quota enforcement (`QuotaPolicy`, `QuotaUsageProvider`, `QuotaChecker`)
- File lifecycle events (`FileEventListener`, `FileEventPublisher`)
- Comprehensive integration and unit tests

## [0.0.9] - 2025-07-10

### Added
- Encryption at rest SPI (`FileEncryptor`, `NoOpFileEncryptor`)
- Batch delete (`FileDeleteService.deleteAll()`, `BatchDeleteResult`)

## [0.0.8] - 2025-07-10

### Added
- ZIP archive listing (`ArchiveMetadataExtractor`)
- EXIF stripping (`ExifStripper`)
- Image format conversion (`ImageFormatConverter`)
- File copy/move (`FileTransferService`)

## [0.0.7] - 2025-07-10

### Added
- Image watermark (`ImageWatermarker`)
- Thumbnail generation (`ThumbnailGenerator`)
- Pre-signed URL generation (`FileStorage.generatePresignedUrl()`)
- PDF metadata extraction (`PdfMetadataExtractor`)
- HTTP range request support (`ByteRange`, `FileResponseBuilder`)

## [0.0.6] - 2025-07-09

### Added
- Spring WebFlux `FilePart` support

## [0.0.5] - 2025-07-09

### Added
- Input validation (filename length, path traversal, bucket name)

## [0.0.4] - 2025-07-09

### Added
- Comprehensive null-check tests

## [0.0.3] - 2025-07-09

### Added
- Streaming upload (temp file buffering, no full memory load)
- File delete service
- `@ConfigurationProperties` support
- CI workflow

## [0.0.2] - 2025-07-08

### Added
- Virus scan SPI
- Image processing (metadata extraction, resize)

## [0.0.1] - 2025-07-08

### Added
- Initial release
- Multi-module structure: `kit-core`, `kit-spring-boot-starter`, `example`
- File validation (`@ValidFile`, `@ValidMultipartFile`)
- Pluggable storage abstraction (`FileStorage`, `FileStorageResolver`)
- Upload with checksum deduplication
- Download with `DownloadResult`
- `LocalFileStorage`, `InMemoryFileStorage`
- Spring Boot auto-configuration
- Maven Central publishing
