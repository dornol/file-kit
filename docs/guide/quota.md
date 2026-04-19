# Storage Quota

Optional per-bucket byte-limit enforcement for uploads, copies, and moves.

## When to use

- Per-tenant storage limits.
- Hard caps on bucket size (e.g. compliance, cost control).
- Per-storage-type quotas (separate LOCAL vs S3 limits).

## Setup

Register two SPI beans — **both required** for quota to be active:

```java
@Component
public class MyQuotaPolicy implements QuotaPolicy {
    @Override
    public long getMaxBytes(Enum<?> storageType, String bucket) {
        return 100 * 1024 * 1024; // 100 MB
    }
}

@Component
public class MyQuotaUsageProvider implements QuotaUsageProvider {
    @Override
    public long getUsedBytes(Enum<?> storageType, String bucket) {
        return fileRepository.sumSizeByBucket(bucket);
    }
}
```

When both are present, `QuotaChecker` is auto-configured and injected into `FileUploadService` and `FileTransferService`. No further wiring needed.

## Behavior

- **Upload**: quota is checked **after deduplication** — dedup hits do not consume quota.
- **Copy / Move**: quota is checked against the **target** bucket.
- **Exceeded**: throws `FileStorageException(QUOTA_EXCEEDED)`.
- **Not registered**: skipped entirely (zero overhead).

## Programmatic usage

```java
QuotaChecker checker = new QuotaChecker(policy, usageProvider);

// Check before custom operations
checker.check(StorageType.S3, "my-bucket", fileSize);

// Query current usage
QuotaUsage usage = checker.getUsage(StorageType.S3, "my-bucket");
usage.usedBytes();       // long
usage.maxBytes();        // long
usage.remainingBytes();  // long
```

## Related

- [storage-spi.md](storage-spi.md) — storage bean registration.
- [download.md](download.md) — upload flow (quota check position).
- [validation-and-errors.md](validation-and-errors.md) — error message keys.
