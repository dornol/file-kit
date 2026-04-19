# Plan: Configurable Temp Directory

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | configurable-temp-directory |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `TempFileBuffer`, `FileUploadService`, `FileTransferService`, `DecryptionHelper`에 optional `Path tempDirectory` 주입 경로 추가. Builder 미지정 시 기존 시스템 tmpdir 동작 유지 |
| Related | 여러 cycle simplify에서 지적된 잠재 flakiness (`countUploadTempFiles` 테스트 격리) + 엔터프라이즈 전용 temp mount 요구 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | 모든 서비스의 temp file이 시스템 tmpdir(`java.io.tmpdir`)에 강제. 테스트 격리 취약 (`FileUploadServiceTest.ingestIoException_propagates_andCleansTempFile`은 시스템 tmpdir 스캔 + prefix 필터로 delta 측정 — CI 병렬/crashed-run 잔여물로 flaky 가능). 프로덕션에선 대용량 업로드용 전용 SSD/tmpfs 마운트 지정 불가 |
| **Solution** | `TempFileBuffer`에 디렉토리 지정 팩토리 추가 + 3개 서비스 Builder에 `tempDirectory(Path)` 옵션. 기본값 `null` → 시스템 tmpdir 유지 (backward compat 100%) |
| **Function/UX Effect** | 테스트 `@TempDir` 주입으로 완전 격리. 프로덕션 `FileUploadService.builder(...).tempDirectory(Paths.get("/mnt/ssd-temp")).build()` 한 줄 |
| **Core Value** | 운영 환경별 temp 배치 제어 + 테스트 신뢰성 동시 확보. 기본 동작 불변 |

---

## 1. 배경

### 1.1 현 strata

모든 temp file 생성은 `Files.createTempFile(PREFIX, SUFFIX)` 사용 — 인자 없는 형태라 시스템 tmpdir 강제:

- `TempFileBuffer.create(String prefix)` (io/)
- `FileUploadService.doUpload` — `TempFileBuffer.create(TEMP_UPLOAD_PREFIX)`, `TempFileBuffer.create(TEMP_ENCRYPTED_PREFIX)`
- `FileTransferService.doCopy` — `TempFileBuffer.create(TEMP_TRANSFER_PREFIX)`
- `DecryptionHelper.decryptToStream` (static method) — `TempFileBuffer.create("file-kit-decrypted-")`
- `LocalFileStorage.upload` — 타겟 디렉토리에 `.upload-` temp (atomic rename 패턴, 별건)

### 1.2 테스트 격리 한계

`FileUploadServiceTest.ingestIoException_propagates_andCleansTempFile`:
```java
long before = countUploadTempFiles(tempDir);  // 시스템 tmpdir 스캔
// ... upload 실패 시키기 ...
long after = countUploadTempFiles(tempDir);
assertEquals(before, after);  // 같은 prefix 가진 다른 파일 영향받을 수 있음
```
실무 flake 발생 안 했으나 CI 병렬 + crashed-run 잔여물에서 가능.

### 1.3 엔터프라이즈 시나리오

- 대용량 업로드 처리용 전용 SSD/tmpfs 파티션 마운트
- 보안 규정: 민감 데이터는 encrypted 파티션에만 임시 저장
- Docker 컨테이너: `/tmp` 크기 제약, 별도 volume 필요
- Disk quota 관리: temp 사용량 전용 디렉토리로 격리

---

## 2. 범위

### 2.1 In Scope

- [ ] `TempFileBuffer.create(Path directory, String prefix)` 오버로드 추가. `directory == null` → 시스템 tmpdir
- [ ] `FileUploadService.Builder.tempDirectory(Path)` 옵션 추가 (기본 null)
- [ ] `FileTransferService.Builder.tempDirectory(Path)` 옵션 추가
- [ ] `DecryptionHelper.decryptToStream(InputStream, FileEncryptor, Path tempDirectory)` 오버로드 + 기존 2-arg는 null 전달로 유지
- [ ] `FileDownloadService.Builder.tempDirectory(Path)` 옵션 (decrypt 경로에 전달)
- [ ] 단위 테스트 — `TempFileBuffer` 신규 케이스, 각 서비스 격리 테스트, `@TempDir` 활용
- [ ] `ingestIoException_propagates_andCleansTempFile`을 `@TempDir` 기반으로 재작성 (`countUploadTempFiles` helper 제거)
- [ ] CHANGELOG

### 2.2 Out of Scope

- `LocalFileStorage`의 atomic-rename temp — 타겟 디렉토리에 생성되어야 하므로 별도 개념
- 기본값 변경 (여전히 시스템 tmpdir)
- Temp directory 자동 생성 / 존재 검증

### 2.3 유보 결정

- **디렉토리 존재 검증**: builder에서 `Files.isDirectory()` 체크 vs 실행 시점
- **null 전달 시맨틱**: system tmpdir 유지 vs IllegalArgumentException
- **`FileDownloadService`의 DecryptionHelper 경로 전달 방식**: Builder에 필드 추가 vs 유틸 호출시 override

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | `TempFileBuffer.create(Path dir, String prefix)` — `dir == null`이면 시스템 tmpdir | High |
| FR-02 | `TempFileBuffer.create(String prefix)` 기존 시그니처 유지 — `create(null, prefix)` 위임 | High |
| FR-03 | `FileUploadService.Builder.tempDirectory(Path)` 옵션 — 기본 null | High |
| FR-04 | `FileTransferService.Builder.tempDirectory(Path)` 옵션 | High |
| FR-05 | `FileDownloadService.Builder.tempDirectory(Path)` 옵션 — decrypt 경로에 전달 | High |
| FR-06 | 디렉토리 미존재 시 `Files.createTempFile` 자체가 `NoSuchFileException` 전파 (wrap 없음, 투명) | Medium |
| FR-07 | `ingestIoException_propagates_andCleansTempFile` `@TempDir` 기반 재작성, `countUploadTempFiles` 제거 | High |

---

## 4. 구현 설계

### 4.1 `TempFileBuffer` 확장

```java
public static TempFileBuffer create(String prefix) throws IOException {
    return create(null, prefix);
}

public static TempFileBuffer create(@Nullable Path directory, String prefix) throws IOException {
    Objects.requireNonNull(prefix, "prefix");
    Path path = directory != null
            ? Files.createTempFile(directory, prefix, SUFFIX)
            : Files.createTempFile(prefix, SUFFIX);
    return new TempFileBuffer(path);
}
```

### 4.2 서비스 변경

각 Builder에 필드 추가:
```java
private @Nullable Path tempDirectory = null;

public Builder tempDirectory(Path tempDirectory) {
    this.tempDirectory = tempDirectory;  // nullable allowed
    return this;
}
```

서비스에서:
```java
try (TempFileBuffer tempFile = TempFileBuffer.create(tempDirectory, TEMP_UPLOAD_PREFIX)) { ... }
```

### 4.3 DecryptionHelper (static)

```java
public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor) {
    return decryptToStream(encryptedContent, fileEncryptor, null);
}

public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor,
                                           @Nullable Path tempDirectory) {
    try (TempFileBuffer buf = TempFileBuffer.create(tempDirectory, "file-kit-decrypted-")) {
        ...
    }
}
```

`FileDownloadService.download()`에서는 새 3-arg 오버로드 호출.

### 4.4 테스트 재작성

**Before** (`ingestIoException_propagates_andCleansTempFile`):
```java
Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
long before = countUploadTempFiles(tempDir);
// ... throw ingest IOException ...
long after = countUploadTempFiles(tempDir);
assertEquals(before, after);
```

**After** (using `@TempDir`):
```java
@TempDir Path tempDir;

@Test
void ingestIoException_propagates_andCleansTempFile() throws IOException {
    FileUploadService svc = FileUploadService.builder(...)
            .tempDirectory(tempDir)
            .build();
    // ... throw ingest IOException ...
    assertThrows(IOException.class, () -> svc.upload(fileSource, ...));

    try (Stream<Path> entries = Files.list(tempDir)) {
        assertEquals(0, entries.count(), "temp file must be deleted");
    }
}
```

완전 격리 + O(N) 스캔 없음.

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `TempFileBuffer.create(Path, String)` + 테스트 | 25분 |
| 2 | Upload/Transfer/Download 서비스 Builder `tempDirectory` 추가 | 30분 |
| 3 | DecryptionHelper 3-arg 오버로드 + FileDownloadService 연결 | 25분 |
| 4 | 기존 `ingestIoException...` 테스트 `@TempDir` 재작성, `countUploadTempFiles` 제거 | 25분 |
| 5 | 회귀 + 빌드 | 15분 |
| 6 | CHANGELOG | 10분 |

총: **약 2시간**

---

## 6. 공개 API

### 추가
- `TempFileBuffer.create(Path, String)` 오버로드
- `FileUploadService.Builder.tempDirectory(Path)`
- `FileTransferService.Builder.tempDirectory(Path)`
- `FileDownloadService.Builder.tempDirectory(Path)`
- `DecryptionHelper.decryptToStream(InputStream, FileEncryptor, Path)` 오버로드

### Breaking
- 없음 — 모든 기존 시그니처 유지, 기본값은 시스템 tmpdir

---

# Design

## 7. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 디렉토리 존재 검증 | **검증 안 함** — `Files.createTempFile`이 `NoSuchFileException` 자연 전파 | 시점-순서 유연성 (Spring bean lazy init 등). 투명한 예외로 충분 |
| null 전달 시맨틱 | **system tmpdir fallback** | Builder default와 일관. `.tempDirectory(null)` 도 명시적 opt-in-default로 사용 가능 |
| DecryptionHelper 경로 전달 | **3-arg 오버로드** (2-arg 유지) | `FileDownloadService.Builder`에만 필드 추가, helper 호출 시점에 명시 전달 |

---

## 8. 테스트 매트릭스

### 8.1 `TempFileBufferTest` 추가

| # | 케이스 |
|---|-------|
| TD1 | `create(Path, String)` — 지정 디렉토리에 파일 생성 확인 |
| TD2 | `create(null, prefix)` — 시스템 tmpdir 사용 (기존 경로) |
| TD3 | `create(Path, null)` — NPE on prefix |
| TD4 | 존재하지 않는 디렉토리 → `NoSuchFileException` 전파 |

### 8.2 서비스 Builder 검증

| # | 케이스 |
|---|-------|
| B1 | `FileUploadService.Builder.tempDirectory(path).build()` — non-null 주입, upload 시 해당 디렉토리에 temp 생성 |
| B2 | `tempDirectory(null)` — 시스템 tmpdir (기존 동작) |
| B3 | Transfer/Download 동일 검증 (간단) |

### 8.3 `ingestIoException...` 재작성

기존 tempdir-scan 로직 제거, `@TempDir` + `Files.list(tempDir).count() == 0` 단순 검증.

---

## 9. Next

`/pdca do configurable-temp-directory`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
