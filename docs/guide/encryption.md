# Encryption at Rest (`FileEncryptor`)

Optional transparent at-rest encryption. Register a `FileEncryptor` bean and files are encrypted before storage, decrypted on download — the application sees plaintext on both ends.

## When to use

- Store files on untrusted backends (shared buckets, third-party hosts).
- Compliance requirements (HIPAA, GDPR, etc.).
- Extra defense-in-depth layer on top of storage-level encryption.

## Implement the SPI

```java
@Component
public class AesFileEncryptor implements FileEncryptor {

    private final SecretKey key;

    @Override
    public void encrypt(InputStream plainInput, OutputStream cipherOutput) throws IOException {
        // CipherOutputStream with AES
    }

    @Override
    public void decrypt(InputStream cipherInput, OutputStream plainOutput) throws IOException {
        // CipherInputStream with AES
    }
}
```

Register as a `@Bean` — the default `NoOpFileEncryptor` is replaced automatically.

## How it works

- **Upload**: checksum is computed on **plaintext** (deduplication still works), then content is encrypted to a temp file before being passed to `FileStorage.upload()`.
- **Download**: encrypted bytes are loaded from storage, then decrypted before returning to the caller.
- **No encryptor registered** (`NoOpFileEncryptor` default): pass-through, zero overhead.

## Size semantics

- `FileMetadata.size` stores the **original plaintext size** (for user-facing display).
- The encrypted size is used only for `FileUploadCommand.contentLength` passed to `FileStorage`.
- Your `FileMetadataRepository` should not assume `size` equals the stored bytes.

## Streaming checksum verification

When `file-kit.verify-checksum-on-download: true`, verification is performed on the **decrypted** stream via `ChecksumVerifyingInputStream` — memory footprint stays O(buffer). Mismatches throw `FileStorageException(CHECKSUM_MISMATCH)`.

## Failure handling

Decryption failures throw `FileStorageException(DECRYPTION_FAILED)`. Decryption temp files are cleaned up on failure (not just on stream close).

## Related

- [storage-spi.md](storage-spi.md) — `FileStorage` contract (receives ciphertext).
- [download.md](download.md) — upload/download flow.
- [validation-and-errors.md](validation-and-errors.md) — encryption-related error keys.
