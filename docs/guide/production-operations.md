# Production operations checklist

`file-kit` provides file processing and storage orchestration. The application
using it must supply the security and lifecycle policy around those operations.

## Authorization boundary

Authorize every operation before calling a file service. In particular,
`FileDownloadService`, `FileDeleteService`, rename, copy, move, and presigned
URL endpoints do not determine whether the caller owns or may access a file.
Use the authenticated principal and the metadata owner/tenant fields in the
application repository to enforce that policy.

Do not expose the example controller unchanged: it is intentionally an
unauthenticated demonstration controller.

## Upload requirements

- Set a finite `file-kit.max-upload-size` and matching web multipart limits.
- Register a real `VirusScanner` for untrusted input.
- `ClamAvVirusScanner` provides a bounded streaming adapter for ClamAV's
  `INSTREAM` protocol. The example registers it when
  `app.clamav.enabled=true`; configure its host/port and network timeouts
  through environment variables in the production profile.
- Register a real `FileEncryptor` and keep
  `file-kit.encryption-required=true` in production.
- Enforce allowed media types and image/archive resource limits per endpoint.
- Keep temporary upload storage on a bounded, private filesystem.

## Metadata and object consistency

- Add a unique database constraint on `checksum` when checksum deduplication
  is enabled.
- Treat storage upload and metadata persistence as a compensating transaction;
  subscribe to `onUploadFailed` for external bookkeeping.
- Run a scheduled orphan cleanup job. The job should list objects from each
  storage backend, compare them with committed metadata, and delete only
  objects older than a safety grace period.
- The example includes an opt-in local-storage job controlled by
  `ORPHAN_CLEANUP_ENABLED`; keep it disabled unless the local storage root and
  metadata lifecycle are the intended source of truth.
- Alert on cleanup failures instead of deleting objects immediately after a
  transient database or storage outage.

## Operational verification

Before release, test large uploads, concurrent duplicate uploads, storage
timeouts, database outages, interrupted downloads, invalid Range headers, and
virus-scan failures. Verify that client responses do not expose filesystem
paths, storage credentials, or backend exception messages.
