# Design: Temp File Buffer — Extract Shared Lifecycle

> **Summary**: scratchpad temp file create/use/cleanup 패턴을 `TempFileBuffer implements Closeable` 로 추출
>
> **Project**: file-kit
> **Version**: 0.1.10 → 0.1.13 (예정)
> **Author**: dhkim
> **Date**: 2026-04-19
> **Status**: Draft
> **Plan**: [temp-file-buffer.plan.md](../../01-plan/features/temp-file-buffer.plan.md)

---

## 1. 설계 목표

- 동일 finally/try-catch 패턴 2곳 → 1개 helper로 응축
- try-with-resources로 cleanup 자동화 (누수 기회 자체 제거)
- 공개 API breaking 0, 성능 변화 0
- 원칙(CLAUDE.md): JDK `Files.createTempFile`을 감싸되, 사용 측 보일러플레이트가 실제로 여러 곳에서 반복되기 때문에 in-scope

### 설계 원칙

- 새 공개 타입 **단 1개**: `TempFileBuffer`
- 상태 최소: `path` + `closed` 플래그만
- 실패 관대: `close()`는 절대 throw하지 않음 (cleanup는 best-effort)
- 테스트 가능: static factory라 mock 어려움 없음 (mock 안 함이 의도)

---

## 2. Plan §2.3 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| `close()` 정책 | **swallow + WARN 로그** | 기존 `FileTransferService`의 "best-effort cleanup" 시맨틱 보존. finally 블록 내 IOException 전파는 suppressed 예외로 디버깅 어려움 |
| `Closeable` vs `AutoCloseable` | **`Closeable`** | 관례 — `java.io` 계열 자원 표준. `close() throws IOException` 시그니처는 규약상 허용, 실제 impl은 throws 없음 |
| 생성자 가시성 | **private** + `public static create(String prefix)` 팩토리만 | 변경 여지 차단, 테스트 우회 생성 방지 |
| suffix 정책 | 항상 `".tmp"` 고정 | 기존 3곳 모두 `.tmp` 사용. 파라미터화할 이득 없음 |
| 이름 | `TempFileBuffer` | MagicByteBuffer와 대칭, io/ 패키지 네이밍 일관성 |

---

## 3. API 정의

### 3.1 `TempFileBuffer`

```java
package io.github.dornol.filekit.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link Path} to a freshly created temporary file, scoped to a
 * try-with-resources block. {@link #close()} deletes the file best-effort.
 *
 * <p>Intended for scratchpad usage inside a single method call. Pair with
 * {@code try-with-resources} so the file is removed on any control-flow
 * path — normal return, thrown exception, or nested block exit.</p>
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (TempFileBuffer tempFile = TempFileBuffer.create("file-kit-upload-")) {
 *     // ... work on tempFile.path() ...
 * } // file is deleted here
 * }</pre>
 *
 * <p><b>Not thread-safe.</b> Each caller should own its own instance.</p>
 *
 * @since 0.1.13
 */
public final class TempFileBuffer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TempFileBuffer.class);
    private static final String SUFFIX = ".tmp";

    private final Path path;
    private boolean closed = false;

    /**
     * Creates a new temporary file via {@link Files#createTempFile(String, String, java.nio.file.attribute.FileAttribute[])}
     * with the given prefix and the {@code .tmp} suffix.
     *
     * @param prefix temp file name prefix (must not be null)
     * @throws IOException if the file cannot be created
     */
    public static TempFileBuffer create(String prefix) throws IOException {
        Objects.requireNonNull(prefix, "prefix");
        return new TempFileBuffer(Files.createTempFile(prefix, SUFFIX));
    }

    private TempFileBuffer(Path path) {
        this.path = path;
    }

    /** Returns the underlying path. Remains non-null after {@link #close()}. */
    public Path path() {
        return path;
    }

    /**
     * Deletes the file best-effort. Safe to call more than once.
     *
     * <p>Any {@link IOException} from the delete is logged at WARN level
     * and swallowed — {@code close()} never throws.</p>
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {} ({})", path, e.getMessage());
        }
    }
}
```

### 3.2 `Closeable` 계약 적합성

- `Closeable.close() throws IOException`이지만, 구현이 throws 없이 완료해도 규약 위반 아님. `@Override`는 signature 일치만 요구.
- 사용자가 `Closeable` 레퍼런스로 받아 `close()` 호출하면 catch(IOException)을 강제받지만, 실제로는 예외가 없음 — 약간의 과도한 catch, 수용.

---

## 4. 서비스 리팩토링 상세

### 4.1 `FileUploadService.doUpload`

**Before** (L272-327):
```java
Path tempFile = Files.createTempFile(TEMP_UPLOAD_PREFIX, ".tmp");
Path encryptedFile = null;
try {
    // ... pass 1, virus scan, dedup, format ...

    encryptedFile = Files.createTempFile(TEMP_ENCRYPTED_PREFIX, ".tmp");
    encryptFile(tempFile, encryptedFile);
    // ... encrypt, upload ...
} finally {
    Files.deleteIfExists(tempFile);
    if (encryptedFile != null) {
        Files.deleteIfExists(encryptedFile);
    }
}
```

**After**:
```java
try (TempFileBuffer tempFile = TempFileBuffer.create(TEMP_UPLOAD_PREFIX)) {
    MagicByteBuffer header = new MagicByteBuffer(formatHeaderBufferSize);
    ChecksumComputation computation = checksumCalculator.newComputation();
    long bytesWritten = teeIngest(fileSource, tempFile.path(), computation, header);
    String checksum = computation.finish();

    scanForVirus(tempFile.path());

    FileMetadata existing = metadataRepository.findByChecksum(checksum);
    if (existing != null) {
        log.info("Duplicate file detected (checksum={}), returning existing metadata: {}",
                checksum, existing.key());
        return existing;
    }

    FileFormat format = formatExtractor.extract(header.asInputStream());
    String key = UUID.randomUUID().toString();
    String name = fileSource.getOriginalFilename() != null
            ? fileSource.getOriginalFilename()
            : key + "." + format.extension();

    try (TempFileBuffer encryptedFile = TempFileBuffer.create(TEMP_ENCRYPTED_PREFIX)) {
        encryptFile(tempFile.path(), encryptedFile.path());
        long encryptedSize = Files.size(encryptedFile.path());

        if (quotaChecker != null) {
            quotaChecker.check(storageType, bucket, encryptedSize);
        }

        FileStorage storage = storageResolver.resolve(storageType);
        FileLocation location;
        try (InputStream is = Files.newInputStream(encryptedFile.path())) {
            location = storage.upload(new FileUploadCommand(
                    key, fileSource.getOriginalFilename(), is, encryptedSize,
                    format.mimeType(), format.extension(), bucket));
        }

        FileMetadata metadata = new FileMetadata(key, name, bytesWritten, checksum, format, location);
        executeCallback(callback, metadata, storage);
        FileMetadata saved = metadataRepository.save(metadata);
        log.info("File uploaded: key={}, size={}, bucket={}, storageType={}",
                saved.key(), saved.size(), bucket, storageType);
        eventPublisher.fireUploaded(saved);
        return saved;
    }
}
```

**주요 변화**:
- finally 블록 제거
- nested try-with-resources로 encryptedFile lazy 생성 보존 (dedup hit 시 생성 skip)
- `tempFile`, `encryptedFile` 사용처는 모두 `.path()` 접근으로 교체
- `doUpload` 시그니처 `throws IOException`은 유지 (teeIngest가 던짐)

### 4.2 `FileTransferService.doCopy`

**Before** (L229-271):
```java
Path tempFile = null;
try {
    tempFile = Files.createTempFile("file-kit-transfer-", ".tmp");
    try (InputStream content = sourceStorage.load(source)) {
        Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
    }
    long actualSize = Files.size(tempFile);

    try (InputStream buffered = Files.newInputStream(tempFile)) {
        FileUploadCommand command = new FileUploadCommand(...);
        FileLocation newLocation = targetStorage.upload(command);
        FileMetadata copied = new FileMetadata(...);
        return metadataRepository.save(copied);
    }
} catch (FileStorageException e) {
    throw e;
} catch (Exception e) {
    throw new FileStorageException(FileStorageException.COPY_FAILED,
            "Failed to copy file: " + source.key(), e);
} finally {
    if (tempFile != null) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) { }
    }
}
```

**After**:
```java
try (TempFileBuffer tempFile = TempFileBuffer.create(TEMP_TRANSFER_PREFIX)) {
    try (InputStream content = sourceStorage.load(source)) {
        Files.copy(content, tempFile.path(), StandardCopyOption.REPLACE_EXISTING);
    }
    long actualSize = Files.size(tempFile.path());

    try (InputStream buffered = Files.newInputStream(tempFile.path())) {
        FileUploadCommand command = new FileUploadCommand(
                newKey, source.name(), buffered, actualSize,
                source.format().mimeType(), source.format().extension(), targetBucket);
        FileLocation newLocation = targetStorage.upload(command);
        FileMetadata copied = new FileMetadata(
                newKey, source.name(), source.size(), source.checksum(),
                source.format(), newLocation);
        return metadataRepository.save(copied);
    }
} catch (FileStorageException e) {
    throw e;
} catch (Exception e) {
    throw new FileStorageException(FileStorageException.COPY_FAILED,
            "Failed to copy file: " + source.key(), e);
}
```

**주요 변화**:
- `Path tempFile = null` + finally block 삭제
- `TEMP_TRANSFER_PREFIX` 상수 추가 (package-private, 테스트 접근 대비)
- catch 절 순서는 그대로 유지 (`FileStorageException`을 먼저, 나머지는 wrap)

### 4.3 상수 이동

현 `FileUploadService`:
```java
static final String TEMP_UPLOAD_PREFIX = "file-kit-upload-";
static final String TEMP_ENCRYPTED_PREFIX = "file-kit-encrypted-";
```

추가:
```java
// FileTransferService
static final String TEMP_TRANSFER_PREFIX = "file-kit-transfer-";
```

상수를 `TempFileBuffer`로 올리지 않음 — prefix는 호출자의 의미론 (디버깅 시 어느 서비스의 temp인지 식별).

---

## 5. 테스트 매트릭스

### 5.1 `TempFileBufferTest` (신규)

| # | 케이스 | 검증 |
|---|-------|------|
| T1 | `create` 성공 — path 반환, 파일 실존 | `Files.exists(buf.path())` |
| T2 | `close` 후 파일 삭제됨 | close 뒤 `!Files.exists()` |
| T3 | `close` 멱등 — 2회 호출 무해 | 두 번째 close 정상 리턴, WARN 없음 |
| T4 | `close` 후 `path()`는 여전히 같은 Path 반환 | 동일 객체 |
| T5 | try-with-resources — 정상 종료 시 cleanup | 블록 exit 후 !exists |
| T6 | try-with-resources — 예외 시 cleanup | 블록 내 throw → !exists |
| T7 | 파일이 이미 지워진 상태에서 close — 예외 없음 | `Files.delete` 호출 전 수동 삭제 후 close, 예외 없음 (deleteIfExists 멱등) |
| T8 | `null` prefix → NPE | `Objects.requireNonNull` |
| T9 | suffix는 `.tmp` | 파일명 endsWith 검증 |

### 5.2 `FileUploadServiceTest` 회귀

기존 테스트 1186건이 그대로 통과해야 함. 특히:
- `ingestIoException_propagates_andCleansTempFile` — tempFile cleanup 검증
- `duplicateChecksum_returnsExistingWithoutUpload` — dedup hit 시 encryptedFile 아예 생성 안 됨을 보장

### 5.3 `FileTransferServiceTest` 회귀

기존 전체 케이스 통과. copy/move/batch 흐름 그대로.

### 5.4 통합 테스트

- `UploadDownloadIntegrationTest`, `EncryptionIntegrationTest`, `BatchTransferIntegrationTest` 녹색
- 별도 통합 시나리오 추가 불필요 (내부 리팩토링)

---

## 6. 예외 계약

| 상황 | 예외 | 발생 지점 |
|------|------|---------|
| `create`: tempdir 없음 / 권한 부족 | `IOException` | static factory |
| `create`: null prefix | `NullPointerException` | `Objects.requireNonNull` |
| `close`: delete 실패 | 로그만 WARN, swallow | close |
| `close` 중복 호출 | no-op | close |

---

## 7. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `TempFileBuffer` + 단위 테스트 T1~T9 | 30분 |
| 2 | `FileUploadService.doUpload` 리팩토링 | 20분 |
| 3 | `FileTransferService.doCopy` 리팩토링 + `TEMP_TRANSFER_PREFIX` 상수 추가 | 15분 |
| 4 | 회귀 테스트 실행 + 수정 | 20분 |
| 5 | JavaDoc + CHANGELOG | 15분 |

총 예상: **약 1.5시간**

---

## 8. 공개 API 변경

### 추가
- `io.github.dornol.filekit.io.TempFileBuffer` (public final, Closeable)

### 변경 (내부)
- `FileUploadService.doUpload()` 구조 — 공개 시그니처 불변
- `FileTransferService.doCopy()` 구조 — private 메서드

### Breaking
- 없음

---

## 9. Next Steps

1. [ ] `/pdca do temp-file-buffer` — 구현 착수
2. [ ] `/pdca analyze temp-file-buffer`
3. [ ] `/pdca report temp-file-buffer`

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
