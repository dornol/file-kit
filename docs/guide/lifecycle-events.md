# File Lifecycle Events (`FileEventListener`)

Publish-subscribe hooks for file operations. Use for audit logging, cache invalidation, statistics, notifications.

## When to use

- Audit trail of all file operations.
- Invalidate caches when files change.
- Emit metrics or domain events.
- React to upload failures (with `onUploadFailed`).

## Implement a listener

```java
@Component
public class AuditFileEventListener implements FileEventListener {

    @Override
    public void onUploaded(FileMetadata metadata) {
        auditLog.record("FILE_UPLOADED", metadata.key(), metadata.size());
    }

    @Override
    public void onDeleted(FileMetadata metadata) {
        auditLog.record("FILE_DELETED", metadata.key());
    }

    // Override only the methods you need — all have default no-op implementations
}
```

Register as a Spring bean (or add to `Collection<FileEventListener>` passed to `FileEventPublisher` in standalone use).

## Events

| Event | Fired | Parameters |
|---|---|---|
| `onUploaded` | After metadata save | uploaded metadata |
| `onUploadFailed` | After storage cleanup on callback or save failure | metadata + cause (`Throwable`) |
| `onDownloaded` | After content load | downloaded metadata |
| `onDeleted` | After storage + metadata deletion | deleted metadata |
| `onCopied` | After copy completes | source + copy metadata |
| `onMoved` | After move completes (copy + source deletion) | source + moved metadata |
| `onRenamed` | After metadata name update | before + after metadata |

## Behavior

- **Fire-and-forget** — listener exceptions are logged and swallowed. A failing listener never breaks the file operation.
- **Multiple listeners** — all registered `FileEventListener` beans are invoked in order.
- **Deduplication** — dedup hits do **not** fire `onUploaded`.
- **Move vs copy** — `move()` fires only `onMoved`, never `onCopied`.
- **Upload failure** — `onUploadFailed` fires for both callback and save failures; subscribe here instead of manually catching `CALLBACK_FAILED`.
- **No listeners** — `FileEventPublisher` is still created (with an empty list); zero overhead.

## Related

- [download.md](download.md) — upload/download/delete flow (event fire points).
- [batch-operations.md](batch-operations.md) — copy/move/rename events.
