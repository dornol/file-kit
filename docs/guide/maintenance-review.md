# Maintenance Review Notes

Internal review notes for follow-up hardening work. These items are not public API
promises; they are concrete places where behavior, tests, or documentation can be
improved.

Last reviewed: 2026-06-22

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
