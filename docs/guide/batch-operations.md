# Batch Operations

Best-effort bulk upload, delete, copy, and move. Every batch result collects successes and failures separately — operations are never aborted on first error.

## Batch upload

```java
@Autowired FileUploadService uploadService;

BatchUploadResult result = uploadService.uploadAll(fileSources, StorageType.LOCAL, "uploads");

result.succeeded();       // List<FileMetadata>
result.failed();          // Map<String, String> — filename → error message
result.failureReasons();  // Map<String, Integer> — reason key → count (aggregation)
result.allSucceeded();    // true if no failures
result.totalRequested();  // total count requested
```

Deduplication applies per file — two files with identical content share existing metadata.

## Batch delete

```java
@Autowired FileDeleteService deleteService;

BatchDeleteResult result = deleteService.deleteAll(List.of("key1", "key2", "key3"));

result.succeeded();       // List<String>
result.failed();          // Map<String, String> — key → error message
result.failureReasons();  // Map<String, Integer>
result.allSucceeded();
result.totalRequested();
```

Storage operations are not transactional — the method attempts every deletion and collects results rather than stopping on first failure.

## File copy / move

Copy or move files between buckets or storage backends:

```java
@Autowired FileTransferService transferService;

// Copy — source remains, new file gets a new UUID key
FileMetadata copied = transferService.copy(fileKey, StorageType.S3, "archive-bucket");

// Move — copy + source deletion
FileMetadata moved = transferService.move(fileKey, StorageType.S3, "archive-bucket");
```

Copy preserves original filename, checksum, and format; assigns a new UUID key and storage location. Move performs copy then deletes source — if source deletion fails after a successful copy, throws `FileStorageException(MOVE_FAILED)`.

Quota checking (when configured) applies to the **target** bucket. Copy fires `onCopied`; move fires **only** `onMoved` (never `onCopied`).

## Batch copy / move

```java
BatchTransferResult copyResult = transferService.copyAll(
        List.of("key1", "key2"), StorageType.S3, "archive-bucket");

BatchTransferResult moveResult = transferService.moveAll(
        List.of("key1", "key2"), StorageType.S3, "archive-bucket");

copyResult.succeeded();       // List<FileMetadata>
copyResult.failed();          // Map<String, String> — source key → error message
copyResult.failureReasons();  // Map<String, Integer>
copyResult.allSucceeded();
copyResult.totalRequested();
```

## Async / parallel variants

Use the async wrappers to run each item concurrently via `CompletableFuture.allOf`:

```java
AsyncFileTransferService asyncTransfer = AsyncFileTransferService.builder(syncTransfer)
        .executor(Executors.newVirtualThreadPerTaskExecutor())  // JDK 21+
        .build();

asyncTransfer.copyAllParallelAsync(keys, StorageType.S3, "bucket");
asyncTransfer.moveAllParallelAsync(keys, StorageType.S3, "bucket");
asyncDelete.deleteAllParallelAsync(keys);
```

See [standalone.md#async-adapters](standalone.md#async-adapters).

## ZIP archive listing

Extract metadata from ZIP archives without fully extracting files:

```java
@Autowired ArchiveMetadataExtractor archiveExtractor;

byte[] zipBytes = Files.readAllBytes(Path.of("archive.zip"));
ArchiveMetadata metadata = archiveExtractor.extract(zipBytes);

metadata.entryCount();
metadata.totalUncompressedSize();

for (ArchiveEntry entry : metadata.entries()) {
    entry.path();
    entry.compressedSize();
    entry.uncompressedSize();
    entry.lastModified();  // nullable
    entry.directory();
}
```

Default `ZipArchiveMetadataExtractor` uses `java.util.zip.ZipInputStream` — no external dependency.

### ZIP bomb protection

Built-in limits:

```java
// Default: 1 GB max uncompressed, 65,535 max entries
ArchiveMetadataExtractor extractor = new ZipArchiveMetadataExtractor();

// Strict
ArchiveMetadataExtractor strict = new ZipArchiveMetadataExtractor(
        100 * 1024 * 1024,  // 100 MB
        1000);               // max 1,000 entries
```

Archives exceeding either limit throw `FileStorageException(ARCHIVE_PROCESSING_FAILED)` immediately — remaining entries are not processed.

## File existence check

Check existence without downloading:

```java
@Autowired FileMetadataRepository metadataRepository;

boolean exists = metadataRepository.existsByKey(fileKey);
```

Default method on `FileMetadataRepository` — no implementation required.

## File rename

Update filename metadata without touching storage:

```java
@Autowired FileRenameService renameService;

FileMetadata renamed = renameService.rename(fileKey, "new-name.txt");
// Only metadata changes; storage bytes untouched; fires onRenamed
```

Same filename validation rules apply (path traversal, length).

## Related

- [download.md](download.md) — single-file upload/download/delete flow.
- [quota.md](quota.md) — target-bucket quota checks on copy/move.
- [lifecycle-events.md](lifecycle-events.md) — `onCopied`, `onMoved`, `onRenamed` events.
- [standalone.md](standalone.md) — async adapters + parallel batch.
