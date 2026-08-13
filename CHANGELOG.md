# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Added
- Streaming `ClamAvVirusScanner` implementation with bounded I/O, timeouts,
  and fail-closed error handling.
- Production-oriented example profile with environment-driven database,
  object storage, upload limits, encryption, and ClamAV settings.
- Opt-in local orphan cleanup example and production operations guidance.

### Fixed
- Spring Boot auto-configuration no longer loads the reactive upload adapter
  in MVC-only applications.
- Multipart validators now receive the configured global upload-size limit
  when instantiated by Bean Validation.
- Example error responses no longer expose internal exception messages.

## [0.2.2] - 2026-08-12

Patch release focused on storage safety, Spring Boot integration, and
operational visibility. No breaking API changes.

### Added
- Optional `StorageHealthCheck` SPI for active storage availability probes.
- Spring Boot 4 Actuator health integration for registered `FileStorage` beans.

### Fixed
- Hardened filename, archive, URL, domain, and storage input validation.
- Added concurrent duplicate-upload protection.
- Improved Spring upload and byte-range handling.
- Hardened the example S3 storage configuration and health probe.

### Documentation
- Documented storage health checks and the safety boundary for orphan cleanup.

## [0.2.1] - 2026-06-22

Patch release focused on hardening upload, local storage, and range-request
edge cases. No breaking API changes.

### Fixed
- `FileUploadService` now enforces `maxUploadSize` while reading the actual
  upload stream, preventing under-reported `FileSource.getSize()` values from
  bypassing the size limit.
- `LocalFileStorage.upload` now validates the real parent path before writing,
  preventing symlinked bucket/object directories from escaping `baseDir`.
- `ByteRange.parse` now consistently reports malformed ranges such as
  `bytes=500` and `bytes=5-3` as
  `FileStorageException(RANGE_NOT_SATISFIABLE)`.

### Documentation
- Added maintenance review notes for the hardening follow-up.
- Clarified streaming SPI memory expectations for custom
  `ChecksumCalculator`, `VirusScanner`, `PdfMetadataExtractor`, and
  `ArchiveMetadataExtractor` implementations.

## [0.2.0] - 2026-04-19

Fifteen-cycle PDCA sweep that addresses every outstanding item from the
internal library review, introduces async and reactive-friendly APIs,
and extends the image-processing surface. No breaking changes —
everything is either additive or preserves prior call-site semantics
via default-method / null-fallback overloads.

### Added
- Streaming checksum verification on download (`ChecksumVerifyingInputStream`)
- New SPI: `ChecksumComputation` (incremental checksum state)
- `ChecksumCalculator.newComputation()` default method with buffering fallback (backward compatible)
- `Sha256ChecksumCalculator` override uses `MessageDigest` directly (true streaming, no buffering)
- `MagicByteBuffer`: captures the first N bytes of an upload stream for format detection
- `FileUploadService.Builder.formatHeaderBufferSize(int)`: configurable header buffer
  (default 16 KiB, minimum 1 KiB)
- `TempFileBuffer` (Closeable): scratchpad temp-file lifecycle helper. Pair with
  try-with-resources for automatic best-effort cleanup. Used internally by
  `FileUploadService` and `FileTransferService`; available in `io/` for any
  caller that needs the same pattern.
- `FileEventListener.onUploadFailed(metadata, cause)` default method: fires
  when an upload fails after the content was written to storage. By the time
  it fires, file-kit has already attempted to delete the file. Covers both
  callback failure and metadata-save failure paths. Subscribe to this event
  for external bookkeeping (quota counter decrement, audit log, failure metric)
  instead of catching `FileStorageException#CALLBACK_FAILED` manually.
- `FileEventPublisher.fireUploadFailed(metadata, cause)` public method.
- `TempFileBuffer.release()`: transfers ownership of the underlying file to the
  caller, making subsequent `close()` a no-op. Enables try-with-resources for
  patterns where the temp file must outlive the block (e.g. wrapping into a
  `DeleteOnCloseInputStream`). Used internally by `DecryptionHelper`.
- New package `io.github.dornol.filekit.async` with `AsyncFileUploadService`
  and `AsyncFileDownloadService` — `CompletableFuture`-returning wrappers
  around the sync services. Executor is injectable via the builder (default
  `ForkJoinPool.commonPool()`); JDK 21+ users should prefer
  `Executors.newVirtualThreadPerTaskExecutor()` for blocking file I/O. No new
  runtime dependencies. Checked `IOException`s surface as the cause of
  `CompletionException`.
- `AsyncFileTransferService`, `AsyncFileDeleteService`, `AsyncFileRenameService`:
  async wrappers covering the remaining sync services. `deleteAsync` returns
  `CompletableFuture<Void>`; all others mirror their sync signatures. No
  checked exceptions to wrap (all three sync services only throw unchecked
  `FileStorageException`), so exception propagation is direct via
  `CompletionException.getCause()`.
- `MediaTypeValidator`, `ImageDimensionValidator`: public final classes extracted
  from `FileValidationHelper` so callers can depend on just the validator they
  need. `FileValidationHelper` is retained as a facade delegating to these
  two validators — its public API is unchanged. Heavy logic moves out of the
  ~281-line helper into focused ~60–90 line classes with their own unit tests.
- `BatchUploadResult.failureReasons()`, `BatchTransferResult.failureReasons()`,
  `BatchDeleteResult.failureReasons()`: aggregate per-file failure messages
  into an immutable `Map<String, Integer>` of reason → count. Complements the
  existing per-file `failed` map for cases where many files fail for the same
  underlying reason (e.g. storage outage) and the per-file view becomes noise.
- `ChecksumAlgorithm` enum (MD5, SHA-1, SHA-256, SHA-384, SHA-512) and
  `MessageDigestChecksumCalculator(ChecksumAlgorithm)` — generic
  `MessageDigest`-backed calculator. `Sha256ChecksumCalculator` is retained
  as a no-arg convenience subclass that calls `super(ChecksumAlgorithm.SHA_256)`;
  existing call sites are unchanged. Users who need other algorithms can now
  write `new MessageDigestChecksumCalculator(ChecksumAlgorithm.MD5)` instead
  of reimplementing the `ChecksumCalculator` SPI.
- `AsyncFileTransferService.copyAllParallelAsync` /
  `moveAllParallelAsync` and `AsyncFileDeleteService.deleteAllParallelAsync`
  — parallel batch methods that submit each item as an independent task on
  the configured executor. Sibling to the sequential `xxxAllAsync` methods
  which remain unchanged. Individual failures land in the result's `failed`
  map without failing the overall future; failure messages unwrap
  `CompletionException` to match the sync-batch format. Result order is
  not guaranteed to match input order (documented). Upload is intentionally
  excluded because parallel execution amplifies the existing dedup
  TOCTOU (`findByChecksum` → `save`) — addressing it requires
  per-checksum coordination that is out of this cycle's scope.
- `DefaultMediaTypeDetector` now runs a magic-byte sniff as its first
  detection pass, before the JDK `URLConnection` probes. Covers PDF,
  ZIP (incl. JAR / DOCX / XLSX / PPTX / APK), PNG, JPEG, GIF, BMP,
  WebP, MP4, OGG, and Zstandard — formats the JDK detector returned
  `application/octet-stream` for. No new runtime dependencies.
  Observable change: inputs that previously fell through to
  `application/octet-stream` now return their actual MIME type;
  validators with allow-lists may need to include the newly-detected
  types.
- Configurable temp directory via `tempDirectory(Path)` on
  `FileUploadService.Builder`, `FileTransferService.Builder`, and
  `FileDownloadService.Builder`. `null` (default) uses the system temp
  directory — existing behavior unchanged. `TempFileBuffer.create(Path, String)`
  overload exposed for any caller that needs the same capability.
  `DecryptionHelper.decryptToStream(..., Path)` three-arg overload added
  so the decryption temp file honors the download service's configured
  directory. Enables per-test isolation via JUnit `@TempDir` and
  production deployments with dedicated temp partitions (SSD/tmpfs,
  Docker volumes, disk-quota-controlled mounts).
- `ImageRotator` / `ImageCropper` SPIs with `ImageIORotator` / `ImageIOCropper`
  default implementations. Rotation supports 90° / 180° / 270° via
  `RotateAngle` enum; cropping takes a pixel-coordinate region via
  `CropOption` with up-front boundary validation (out-of-range →
  `IllegalArgumentException`). Option/Result records mirror the existing
  resize / watermark shape. Reuses `ImageIOUtils` for read / write /
  output-format resolution so JPEG RGB conversion and quality handling
  stay consistent across image operations.
- `SignedUrlSigner` (new `io.github.dornol.filekit.url` package) — HMAC-SHA256
  helper for generating and verifying time-limited download URLs against
  local-storage file servers. Produces `"exp=...&sig=..."` query fragments;
  verification uses constant-time comparison
  (`MessageDigest.isEqual`). Secret key must be at least 16 bytes.
  Accompanying exceptions: `SignedUrlException` (base),
  `SignedUrlExpiredException`, `SignedUrlInvalidSignatureException`.
  The helper deliberately does not build full URLs or authorize users —
  authorization remains an application concern; a valid signature only
  proves the URL originated from the app.

### Changed (upload pipeline I/O reduction)
- `FileUploadService.doUpload()`: consolidated ingest pass. Source is now teed into
  tempFile + `ChecksumComputation` + `MagicByteBuffer` in a single read, eliminating
  two separate tempFile re-reads (checksum pass, format-detection pass).
  tempFile is now read twice (virus scan + encrypt), down from four times.
- **Pipeline order**: virus scan now runs before dedup check. Duplicate-file detection
  happens after virus scan but before format extraction / encryption / upload, so
  dedup hits still skip format/encrypt/upload as before.
- **Format detection**: `FileFormatExtractor.extract()` is now invoked with an
  `InputStream` backed by the header buffer (up to 16 KiB by default) instead of
  the full tempFile. Detectors relying on bytes beyond the buffer must increase
  `formatHeaderBufferSize` via the builder.
- **Infected files**: checksum is now computed during ingest regardless of virus
  status (consequence of the tee pattern). Previous behavior skipped checksum on
  infected files; the savings from eliminating tempFile re-reads on clean files
  (the common path) outweigh the marginal cost on infected files.
- `FileUploadService.doUpload` / `FileTransferService.doCopy`: temp-file cleanup
  moved from manual `finally` blocks to `try-with-resources` via
  `TempFileBuffer`. Upload's encrypted-temp remains lazy (not created on
  dedup-hit) via a nested try-with-resources block. `IOException` raised by
  `Files.deleteIfExists` is now logged at WARN and swallowed uniformly across
  both services (previously the Upload path could propagate it).
- **Upload failure cleanup**: `FileUploadService.doUpload()` now deletes the
  uploaded file from storage when `FileMetadataRepository.save` fails (previously
  a save failure left an orphan in storage). The original save exception is
  re-thrown unchanged; any cleanup failure is recorded via `addSuppressed`.
- **Callback failure cleanup**: `executeCallback` similarly records any
  `storage.delete` failure via `addSuppressed` on the wrapped
  `FileStorageException(CALLBACK_FAILED)` (previously the delete failure was
  unobservable).
- **Failure observability**: `onUploadFailed` fires after storage cleanup for
  both callback and save failures — the metadata delivered to the listener is
  the in-memory instance (not persisted). Listeners must not re-delete from
  storage.
- `DecryptionHelper.decryptToStream`: manual null-check + finally-block
  cleanup replaced with `TempFileBuffer` + `release()` in a try-with-resources.
  Behavior is unchanged: on success the temp file is owned by the returned
  `DeleteOnCloseInputStream`; on failure the temp file is deleted best-effort
  and `FileStorageException(DECRYPTION_FAILED)` is thrown.

### Changed
- `FileDownloadService.download()`: when a `ChecksumCalculator` is configured, the returned
  `InputStream` now verifies the checksum on-the-fly as it is read. Memory footprint is
  O(buffer) regardless of file size (previously O(file) via `readAllBytes()`).
- **Behavioral**: `CHECKSUM_MISMATCH` is now thrown from the `read()` call that reaches EOF,
  not from `download()` itself. Callers must consume the stream to completion to receive
  the mismatch exception. Closing the stream before EOF skips verification (WARN logged).
- **Event semantics**: `FileEventListener.onDownloaded` now fires when the download stream
  is returned to the caller, not after the checksum has been fully verified. A subsequent
  `CHECKSUM_MISMATCH` during stream consumption will therefore be preceded by a successful
  `onDownloaded` event. Consumers that treat this event as "integrity-verified" must
  either (a) verify via `Sha256ChecksumCalculator#checksum(InputStream)` themselves after
  consumption, or (b) wait for stream close without exception as the integrity signal.

### Migration notes
- Custom `ChecksumCalculator` implementations continue to work via the default buffering
  fallback, but the fallback buffers the entire input in memory — override
  `newComputation()` with a streaming implementation (e.g. wrap `MessageDigest`) to avoid
  OOM on large files. A WARN is logged once per JVM when the fallback is first used.
- Consumers that relied on `download()` throwing `CHECKSUM_MISMATCH` synchronously must
  move their `try/catch` around the stream consumption (`readAllBytes`, `transferTo`, etc).

## [0.1.10] - 2025-07-12

### Added
- Encryption + checksum verification integration tests (pass + corruption detection)
- `CHANGELOG.md`: version history from 0.0.1 to 0.1.9
- Example endpoints: exists, rename, batch upload (uploadAll), batch copy/move

### Changed
- Example `upload-multiple` now uses `uploadAll` instead of manual loop
- Example stream endpoint: replaced FQN with imports
- `PdfKitAutoConfigurationTest`: replaced FQN with import

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
