# Maintenance Review Notes

Internal review notes for follow-up hardening work. These items are not public API
promises; they are concrete places where behavior, tests, or documentation can be
improved.

Last reviewed: 2026-08-12

## Operational safeguards

### Domain value validation

Status: implemented on 2026-08-12.

`FileMetadata`, `FileLocation`, `FileFormat`, and `FileUploadCommand` reject
blank/control-character values and unsafe relative object keys. Hierarchical
keys remain supported for date- and hash-based storage strategies.

### Metrics cardinality

Status: implemented on 2026-08-12.

Bucket is no longer a metric tag by default. Set
`file-kit.metrics-include-bucket=true` only when bucket names are from a small,
fixed set.

### Concurrent checksum deduplication

Status: partially implemented on 2026-08-12.

The checksum lookup and metadata save are not an atomic operation. Repository
implementations should put a unique constraint on the checksum column. When a
constraint violation occurs, `FileUploadService` re-reads the existing metadata
by checksum and returns it after cleaning up the newly uploaded storage object.
Repositories still need to define the unique constraint and use a
constraint-violation exception that preserves the failed `save` behavior when
no concurrent row can be found.

### Streaming SPI implementations

The compatibility defaults on `ChecksumCalculator`, `VirusScanner`,
`PdfMetadataExtractor`, and `ArchiveMetadataExtractor` may buffer an entire
stream. Production implementations handling untrusted or large files should
override the stream method (and `ChecksumCalculator.newComputation()`) with a
bounded streaming implementation. The upload pipeline itself remains
streaming and enforces the configured upload limit while reading.

## Priority Items

### Enforce upload size while reading

Status: implemented on 2026-06-22.

`FileUploadService` currently checks `maxUploadSize` against
`FileSource.getSize()` before reading the stream. The ingest path should also
count actual bytes while copying to the temp file and stop with
`FileStorageException.FILE_TOO_LARGE` as soon as the configured limit is
exceeded.

Why it matters:

- `FileSource.getSize()` is metadata supplied by the adapter or client-side
  framework and can be inaccurate.
- A source reporting `0` or a smaller size can still stream a much larger body.
- Without streaming enforcement, oversized uploads can consume temp disk before
  validation fails.

Suggested test:

- Create a `FileSource` whose `getSize()` returns `0` but whose stream contains
  more than `maxUploadSize`; assert upload fails with `FILE_TOO_LARGE` and no
  storage upload occurs.

### Harden local upload path handling against symlinks

Status: implemented on 2026-06-22.

`LocalFileStorage` validates normalized paths before writing, and load-time
validation resolves real paths. Upload should also validate the real parent path
before creating temp files or moving the final object.

Why it matters:

- A bucket directory inside `baseDir` could be a symlink to an external
  directory.
- Normalized path checks do not detect that escape because the symlink is
  resolved by the filesystem during write.
- This can allow local storage uploads to write outside `baseDir`.

Suggested test:

- Create `baseDir/bucket` as a symlink to a directory outside `baseDir`.
- Upload into `bucket`.
- Assert upload is rejected and the outside directory does not receive the file.

### Normalize invalid Range header errors

Status: implemented on 2026-06-22.

`ByteRange.parse` should consistently throw
`FileStorageException.RANGE_NOT_SATISFIABLE` for malformed range specs. Inputs
such as `bytes=500` can currently fall through to an unchecked parsing error
instead of the expected storage exception.

Why it matters:

- HTTP download controllers can map `RANGE_NOT_SATISFIABLE` to `416`.
- Unexpected runtime exceptions are more likely to become `500` responses.

Suggested tests:

- `ByteRange.parse("bytes=500", 1000)` throws `FileStorageException` with
  `RANGE_NOT_SATISFIABLE`.
- `ByteRange.parse("bytes=5-3", 1000)` throws the same exception type and key.

## Lower-Risk Follow-Ups

### Make streaming SPI expectations more explicit

Status: implemented on 2026-06-22.

`FileUploadService` uses `ChecksumCalculator.newComputation()` during ingest.
The default implementation buffers all bytes before delegating to
`checksum(byte[])`, so custom checksum implementations that do not override
`newComputation()` can still have O(file size) memory behavior.

Options:

- Strengthen documentation around implementing `newComputation()` for large or
  untrusted inputs.
- Add a test or sample custom checksum calculator that demonstrates streaming
  behavior.
- Consider logging or documenting a warning when using the default buffering
  computation in large-file scenarios.

### Review full-buffer default methods

Status: implemented on 2026-06-22.

Several SPI default methods intentionally call `readAllBytes()` for convenience:

- `VirusScanner.scan(InputStream)`
- `PdfMetadataExtractor.extract(InputStream)`
- `ArchiveMetadataExtractor.extract(InputStream)`
- `ChecksumCalculator.checksum(InputStream)`

This is acceptable for compatibility, but docs should keep reminding users to
override these defaults for large or untrusted files.

## Verification Snapshot

At review time, the full test suite passed:

```bash
./gradlew test
```
