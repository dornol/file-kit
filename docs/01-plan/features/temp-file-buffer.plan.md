# Plan: Temp File Buffer — Extract Shared Lifecycle

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | temp-file-buffer |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `FileUploadService` + `FileTransferService`의 scratchpad temp file 수명주기를 `TempFileBuffer` helper로 추출 |
| Related | `docs/review/2026-04-19-library-review.md` (R3). R2 사이클 후 `FileUploadService.doUpload()`에 tempFile + encryptedFile 2개 관리 코드 그대로 유지됨 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `FileUploadService.doUpload()`(L272-327)와 `FileTransferService.doCopy()`(L231-271)이 동일한 "createTempFile → try { work } finally { deleteIfExists }" 패턴 중복. Upload는 temp 2개 관리로 nested null-check + finally 복잡, Transfer는 best-effort delete를 위한 try-catch 중첩 |
| **Solution** | `TempFileBuffer implements Closeable` 도입. try-with-resources로 cleanup 중앙화. Nested resource로 lazy 두 번째 temp file 지원 |
| **Function/UX Effect** | 코드 직관성 ↑, 누수 위험 ↓, 기능·성능 변화 0 |
| **Core Value** | 순수 코드 품질 개선 — 동일 패턴 3회 재사용 가능한 타입으로 응축, 향후 temp file 다루는 코드의 정석 제공 |

---

## 1. 배경

### 1.1 현황 중복

**FileUploadService.java:272-327** — 2개 temp file, nested cleanup
```java
Path tempFile = Files.createTempFile(TEMP_UPLOAD_PREFIX, ".tmp");
Path encryptedFile = null;
try {
    // ... teeIngest(...) ...
    // ... virus scan ...
    // ... dedup check ...

    encryptedFile = Files.createTempFile(TEMP_ENCRYPTED_PREFIX, ".tmp");
    // ... encrypt + upload ...
} finally {
    Files.deleteIfExists(tempFile);               // ← IOException 던질 수 있음
    if (encryptedFile != null) {
        Files.deleteIfExists(encryptedFile);      // ← 마찬가지
    }
}
```

**FileTransferService.java:231-271** — 1개 temp file, best-effort delete
```java
Path tempFile = null;
try {
    tempFile = Files.createTempFile("file-kit-transfer-", ".tmp");
    // ... copy + upload ...
} finally {
    if (tempFile != null) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {           // ← best-effort swallow
            // best-effort cleanup
        }
    }
}
```

### 1.2 문제점

- **중복**: 동일 패턴 2곳, 각 15~20줄
- **비대칭**: Upload는 `deleteIfExists`의 IOException을 전파할 수 있음 (finally 내 throw → suppressed 이슈), Transfer는 swallow. Close 시맨틱 통일 필요
- **null 체크 노이즈**: Upload의 `encryptedFile != null` 가드가 lazy init 때문에 필수. try-with-resources로 교체 가능
- **반복의 함정**: 미래에 temp file 쓰는 세 번째 서비스가 생기면 또 같은 패턴 복붙

### 1.3 범위 경계 (R3 원래 범위)

- ✅ Upload의 2 temp files
- ✅ Transfer의 1 temp file
- ⏸ `DecryptionHelper.java:38`의 temp file — release-on-success 시맨틱 (temp 소유권을 `DeleteOnCloseInputStream`으로 이전)이 달라 별건. TempFileBuffer에 `release()` 추가하는 확장은 가능하나 이번 범위 밖
- ❌ `LocalFileStorage.java:84`의 temp — atomic-rename 패턴 (target 디렉토리 내 staging). 완전 다른 목적

---

## 2. 범위

### 2.1 In Scope

- [ ] `TempFileBuffer implements Closeable` 신설 (io/)
- [ ] `TempFileBuffer.create(String prefix)` 정적 팩토리, suffix는 항상 `.tmp`
- [ ] `path()` accessor
- [ ] `close()`는 `Files.deleteIfExists()`의 IOException을 swallow (현 Transfer 시맨틱 통일)
- [ ] `FileUploadService.doUpload()` 리팩토링 — try-with-resources + nested for encryptedFile
- [ ] `FileTransferService.doCopy()` 리팩토링 — try-with-resources
- [ ] 단위 테스트 (create/path/close/double-close/close-with-missing-file)
- [ ] 기존 테스트 회귀 0
- [ ] CHANGELOG `[Unreleased]` 엔트리

### 2.2 Out of Scope

- `DecryptionHelper` 리팩토링 (release 시맨틱 별건)
- `LocalFileStorage` atomic-write 패턴 (목적 상이)
- Testable tempdir 주입 (R5 급 별건)
- `TempFileBuffer`의 `release()` 메서드 (DecryptionHelper 흡수할 때 필요, 지금은 YAGNI)

### 2.3 Design 단계에서 확정

- `close()` 정책: IOException swallow vs 전파 → 현 Transfer 시맨틱 (swallow + DEBUG log) 추천, Design에서 확정
- `TempFileBuffer` 가시성: `public final` in `io/` (기존 `MagicByteBuffer`, `BoundedInputStream` 패턴 따름)
- 생성자 expose 여부: private + static factory only (변경 여지 차단)
- `Closeable` vs `AutoCloseable`: `Closeable` 선택 — `close() throws IOException` 시그니처 (실제 swallow하므로 throws 선언 불필요하지만 관례상)

---

## 3. 요구사항

### 3.1 기능 요구사항

| ID | 요구사항 | 우선순위 | 상태 |
|----|---------|---------|------|
| FR-01 | `TempFileBuffer.create(prefix)`가 `Files.createTempFile`의 동작 그대로 (IOException 전파) | High | Pending |
| FR-02 | `path()`는 항상 유효한 Path 반환 (close 후에도, 파일은 지워짐) | Medium | Pending |
| FR-03 | `close()`는 멱등 — 두 번 호출해도 안전, 두 번째는 no-op | High | Pending |
| FR-04 | `close()`가 `deleteIfExists` IOException 발생 시 swallow, WARN 로그 기록 | High | Pending |
| FR-05 | `FileUploadService.doUpload()`가 try-with-resources로 tempFile + (nested) encryptedFile 관리 | High | Pending |
| FR-06 | `FileTransferService.doCopy()`가 try-with-resources로 tempFile 관리 | High | Pending |
| FR-07 | 정상 경로 / 예외 경로 / 중간 실패 모두 cleanup 보장 | High | Pending |

### 3.2 비기능 요구사항

| 항목 | 기준 | 측정 |
|------|------|------|
| 공개 API breaking | 0 | `./gradlew build` |
| 기존 테스트 회귀 | 0 실패 | CI |
| 성능 | 변경 없음 (동일 시스템콜 회수) | 관찰 |
| LOC | Upload + Transfer 합쳐 ~20줄 감소 | diff |

---

## 4. 설계 개요

### 4.1 TempFileBuffer

```java
public final class TempFileBuffer implements Closeable {

    private final Path path;
    private boolean closed = false;

    public static TempFileBuffer create(String prefix) throws IOException {
        return new TempFileBuffer(Files.createTempFile(prefix, ".tmp"));
    }

    private TempFileBuffer(Path path) { this.path = path; }

    public Path path() { return path; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {} ({})", path, e.getMessage());
        }
    }
}
```

### 4.2 FileUploadService 리팩토링

```java
try (TempFileBuffer tempFile = TempFileBuffer.create(TEMP_UPLOAD_PREFIX)) {
    // Pass 1: tee ingest
    MagicByteBuffer header = new MagicByteBuffer(formatHeaderBufferSize);
    ChecksumComputation computation = checksumCalculator.newComputation();
    long bytesWritten = teeIngest(fileSource, tempFile.path(), computation, header);
    String checksum = computation.finish();

    scanForVirus(tempFile.path());

    FileMetadata existing = metadataRepository.findByChecksum(checksum);
    if (existing != null) {
        log.info("Duplicate file detected ...");
        return existing;
    }

    FileFormat format = formatExtractor.extract(header.asInputStream());
    String key = UUID.randomUUID().toString();
    String name = ...;

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
        log.info("File uploaded: ...");
        eventPublisher.fireUploaded(saved);
        return saved;
    }
}
```

### 4.3 FileTransferService 리팩토링

```java
try (TempFileBuffer tempFile = TempFileBuffer.create("file-kit-transfer-")) {
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

---

## 5. 성공 기준

### 5.1 Definition of Done

- [ ] `TempFileBuffer` 신규 파일 + 단위 테스트
- [ ] `FileUploadService`, `FileTransferService` 리팩토링
- [ ] 기존 테스트 회귀 0 (FileUploadServiceTest 1186+, FileTransferServiceTest, 통합 테스트)
- [ ] `FileUploadService.TEMP_UPLOAD_PREFIX`/`TEMP_ENCRYPTED_PREFIX` 상수 그대로 유지 (테스트 의존)
- [ ] CHANGELOG 엔트리

### 5.2 품질 기준

- [ ] `./gradlew build` 성공
- [ ] 공개 API breaking 0
- [ ] LOC: Upload + Transfer 합쳐 순감소 (목표 -20줄 이상)

---

## 6. 위험 및 완화

| 위험 | 영향 | 가능성 | 완화 |
|------|------|-------|------|
| Nested try-with-resources가 가독성 떨어짐 | Low | Medium | 들여쓰기 관리, 필요시 inner 블록을 private 메서드로 추출 |
| `close()` 에서 swallow한 IOException으로 누수 은폐 | Medium | Low | WARN 로그 필수. 현 Transfer 시맨틱 유지 |
| 테스트 helper `countUploadTempFiles`가 상수 참조로 바뀌었는데 prefix 변경 없어야 함 | Medium | Low | 상수 이름 그대로 유지 |
| 기존 `FileUploadService.doUpload` finally에서 IOException 흡수 안 했던 것이 이번에 swallow되면서 동작 차이 | Low | Low | 기존 `Files.deleteIfExists(tempFile)`가 IOException 던지는 경우 희박 + 기존 `throws IOException` 선언이라 호출부는 이미 처리. 시맨틱 약간 더 관대해지는 방향 |

---

## 7. 구현 순서 (예상)

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `TempFileBuffer` + 단위 테스트 | 30분 |
| 2 | `FileUploadService.doUpload` 리팩토링 | 20분 |
| 3 | `FileTransferService.doCopy` 리팩토링 | 15분 |
| 4 | 회귀 테스트 확인 | 20분 |
| 5 | JavaDoc + CHANGELOG | 15분 |

총 예상: **약 1.5시간**

---

## 8. 공개 API 변경 요약

### 추가
- `io.github.dornol.filekit.io.TempFileBuffer` (public final, Closeable)

### 변경
- `FileUploadService.doUpload()` 내부 구조 (공개 시그니처 불변)
- `FileTransferService.doCopy()` 내부 구조 (private 메서드, 공개 영향 0)

### Breaking
- 없음

### 마이그레이션 노트
- 사용자 영향 없음. 내부 리팩토링만.

---

## 9. Next Steps

1. [ ] `/pdca design temp-file-buffer` — §2.3 유보 결정 확정
2. [ ] 구현 착수 (`/pdca do temp-file-buffer`)
3. [ ] Gap 분석 → 보고서

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
