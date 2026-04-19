# Design: Upload Pipeline I/O Reduction

> **Summary**: `FileUploadService.doUpload()`의 tempFile 재읽기 4회를 2회로 축소
>
> **Project**: file-kit
> **Version**: 0.1.10 → 0.1.12 (예정, streaming-checksum-verify 이후)
> **Author**: dhkim
> **Date**: 2026-04-19
> **Status**: Draft
> **Plan**: [upload-pipeline-io.plan.md](../../01-plan/features/upload-pipeline-io.plan.md)

---

## 1. 설계 목표

- dedup miss 경로: tempFile read 4회(virus/checksum/format/encrypt) → **2회**(virus/encrypt)
- dedup hit 경로: format/encrypt/upload 생략 (기존: dedup hit도 format 추출까지 모두 수행은 아님, 그러나 중복 감지 전에 이미 virus+checksum full read 소비)
- 공개 API breaking 없음. SPI 변경 없음
- 원칙(CLAUDE.md): 범위 밖 확장 금지. `VirusScanner` 증분화·encrypt+upload 파이프라이닝은 별건

### 설계 원칙

- **재사용**: R1 사이클에서 도입한 `ChecksumComputation` SPI를 ingest pass에서 재사용
- **새 공개 타입 1개**: `MagicByteBuffer` (io/) — 다른 io 유틸(`BoundedInputStream`, `ChecksumVerifyingInputStream`)과 동일 위치/가시성
- **보수적 보안 기본값**: dedup hit에서도 virus scan 실행. 스킵은 별도 feature로 유보

---

## 2. Plan 유보 결정사항 확정 (§2.3)

| 쟁점 | 결정 | 근거 |
|------|------|------|
| Magic byte 버퍼 기본 크기 | **16 KiB** (16384 bytes). `FileUploadService.Builder.formatHeaderBufferSize(int)`로 조정 가능, 최소 1 KiB | Apache Tika `Detector`의 peek 기본값과 일치. 대부분 포맷(PNG 8B, PDF 5B, ZIP 4B, XML wrapper 등)은 수백 바이트 이내로 판정 |
| Format 추출 실패 시 fallback | **없음**. 버퍼가 권위적. 16 KiB로 판정 불가한 구현체는 `formatHeaderBufferSize`를 키우거나 `FileSource`에서 직접 처리 | tempFile 재읽기 허용 시 설계 복잡도↑ + "형식 판정이 16 KiB로 부족한가?" 판단 로직의 비결정성. 커스텀 extractor 드물고 대개 Tika 기반 |
| Dedup hit 시 virus scan | **항상 실행(skip 안 함)**. Dedup은 virus scan 뒤에 수행 | 서명 DB 업데이트로 과거엔 clean이던 파일이 현재엔 감염일 수 있음. 방어적 기본값 유지. "skip 허용" 정책은 별건 피처 |
| `MagicByteBuffer` 가시성 | **`public final`** in `io/` 패키지 | `BoundedInputStream`·`ChecksumVerifyingInputStream`과 동일 패턴. `FileTransferService` 등에서 추후 재사용 여지 |

### 파이프라인 재정렬 결과

```
 source ──► TeeOutputStream ──► tempFile (write)
                │       │
                │       └─► MagicByteBuffer.observe(buf, off, n)  (앞 N 바이트 캐시)
                │
                └─► ChecksumComputation.update(...)

 after ingest:
   1. checksum = computation.finish()
   2. scanForVirus(tempFile)                          ◄── 항상 실행
   3. metadataRepository.findByChecksum(checksum)
        └─► hit? return existing, skip format/encrypt/upload
   4. format = formatExtractor.extract(header.asInputStream())  ◄── 버퍼만 사용
   5. encryptFile(tempFile → encryptedFile)
   6. storage.upload(encryptedFile)
```

---

## 3. 컴포넌트 구조

### 3.1 파일 위치

| 분류 | 파일 | 공개도 |
|------|------|-------|
| 신규 유틸 | `kit-core/src/main/java/io/github/dornol/filekit/io/MagicByteBuffer.java` | `public final` |
| 서비스 수정 | `kit-core/src/main/java/io/github/dornol/filekit/upload/FileUploadService.java` | 기존 |
| 서비스 Builder | `FileUploadService.Builder` — `formatHeaderBufferSize(int)` 추가 | 기존 |
| 신규 테스트 | `kit-core/src/test/java/io/github/dornol/filekit/io/MagicByteBufferTest.java` | test |
| 테스트 추가 | `FileUploadServiceTest` 기존 파일에 dedup fast-path 케이스 추가 | 기존 |
| 통합 테스트 | `BatchUploadIntegrationTest` 혹은 별도 — read 횟수 측정 | test |

### 3.2 클래스 관계

```
┌────────────────────────────────┐
│ FileUploadService              │
│  - checksumCalculator          │
│  - formatHeaderBufferSize      │◄── 신규 필드 (default 16384)
│  doUpload(...)                 │
└────────────┬───────────────────┘
             │ ingest pass 내부에서 사용
             ▼
  ┌──────────────────────────────────┐
  │ Files.newOutputStream(tempFile)  │
  │        │                         │
  │        ▼                         │
  │ Tee logic (inline)               │
  │  ├─► write bytes                 │
  │  ├─► checksum.update(buf,off,n)  │◄── R1 SPI 재사용
  │  └─► header.observe(buf,off,n)   │
  │           │                      │
  │           ▼                      │
  │  ┌──────────────────────┐        │
  │  │ MagicByteBuffer      │        │
  │  │  observe(...)        │        │
  │  │  asInputStream()     │        │
  │  │  size()              │        │
  │  └──────────────────────┘        │
  └──────────────────────────────────┘
```

---

## 4. API 정의

### 4.1 `MagicByteBuffer` (신규)

```java
package io.github.dornol.filekit.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Accumulates the first N bytes observed from a stream for file-format
 * detection, without retaining the rest of the stream.
 *
 * <p>Intended for use alongside a write path that tees incoming bytes through
 * {@link #observe(byte[], int, int)}. Once the buffer is full, additional
 * observe calls are ignored — only the header is retained.</p>
 *
 * <p>Not thread-safe. Safe for single-producer, then single-consumer usage
 * via {@link #asInputStream()}.</p>
 *
 * @since 0.1.12
 */
public final class MagicByteBuffer {

    public static final int DEFAULT_SIZE = 16 * 1024;   // 16 KiB
    public static final int MIN_SIZE = 1024;             // 1 KiB

    private final byte[] buffer;
    private int size = 0;

    /** Creates a buffer with {@link #DEFAULT_SIZE} capacity. */
    public MagicByteBuffer() { this(DEFAULT_SIZE); }

    /**
     * @param capacity buffer capacity in bytes, minimum {@link #MIN_SIZE}
     * @throws IllegalArgumentException if {@code capacity < MIN_SIZE}
     */
    public MagicByteBuffer(int capacity) {
        if (capacity < MIN_SIZE) {
            throw new IllegalArgumentException(
                "capacity must be at least " + MIN_SIZE + ", got " + capacity);
        }
        this.buffer = new byte[capacity];
    }

    /**
     * Copies up to {@code len} bytes from {@code buf[off..off+len)} into the
     * internal buffer, until capacity is reached. Subsequent calls beyond
     * capacity are silently ignored.
     */
    public void observe(byte[] buf, int off, int len) {
        if (size >= buffer.length) return;
        int remaining = buffer.length - size;
        int toCopy = Math.min(remaining, len);
        System.arraycopy(buf, off, buffer, size, toCopy);
        size += toCopy;
    }

    /** Returns the number of bytes captured so far. */
    public int size() { return size; }

    /** Returns the buffer capacity. */
    public int capacity() { return buffer.length; }

    /**
     * Returns a new {@link InputStream} over the captured bytes.
     * The stream reads only what {@link #observe} actually captured — if the
     * source was shorter than capacity, the stream is correspondingly short.
     */
    public InputStream asInputStream() {
        return new ByteArrayInputStream(buffer, 0, size);
    }
}
```

### 4.2 `FileUploadService.Builder.formatHeaderBufferSize`

```java
/**
 * Sets the size of the header buffer used for format detection. A larger
 * buffer improves detection for formats that require a longer magic-byte
 * prefix (e.g. some XML wrappers) at the cost of per-upload memory.
 *
 * <p>Default: {@link MagicByteBuffer#DEFAULT_SIZE} (16 KiB).</p>
 *
 * @throws IllegalArgumentException if {@code bytes < MagicByteBuffer#MIN_SIZE}
 * @since 0.1.12
 */
public Builder formatHeaderBufferSize(int bytes) { ... }
```

### 4.3 `doUpload` 재작성 (의사코드)

```java
Path tempFile = Files.createTempFile("file-kit-upload-", ".tmp");
Path encryptedFile = null;
try {
    // ── Pass 1: ingest + tee ─────────────────────────────────────
    MagicByteBuffer header = new MagicByteBuffer(formatHeaderBufferSize);
    ChecksumComputation computation = checksumCalculator.newComputation();
    long bytesWritten;
    try (InputStream is = fileSource.getInputStream();
         OutputStream fos = Files.newOutputStream(tempFile)) {
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = is.read(buf)) != -1) {
            fos.write(buf, 0, n);
            computation.update(buf, 0, n);
            header.observe(buf, 0, n);
            total += n;
        }
        bytesWritten = total;
    }
    String checksum = computation.finish();

    // ── Post-ingest decisions ────────────────────────────────────
    scanForVirus(tempFile);                       // 항상 실행 (보수적)

    FileMetadata existing = metadataRepository.findByChecksum(checksum);
    if (existing != null) {
        log.info("Duplicate file detected (checksum={}), returning existing metadata: {}",
                 checksum, existing.key());
        return existing;                          // fast-path: format/encrypt/upload 스킵
    }

    FileFormat format = formatExtractor.extract(header.asInputStream());

    String key = UUID.randomUUID().toString();
    String name = fileSource.getOriginalFilename() != null
            ? fileSource.getOriginalFilename()
            : key + "." + format.extension();

    // ── encrypt + upload (기존과 동일) ─────────────────────────
    encryptedFile = Files.createTempFile("file-kit-encrypted-", ".tmp");
    encryptFile(tempFile, encryptedFile);
    long encryptedSize = Files.size(encryptedFile);

    if (quotaChecker != null) {
        quotaChecker.check(storageType, bucket, encryptedSize);
    }
    FileStorage storage = storageResolver.resolve(storageType);
    FileLocation location;
    try (InputStream is = Files.newInputStream(encryptedFile)) {
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
} finally {
    Files.deleteIfExists(tempFile);
    if (encryptedFile != null) Files.deleteIfExists(encryptedFile);
}
```

### 4.4 순서·시맨틱 변경 요약

| 단계 | 변경 전 | 변경 후 |
|------|---------|---------|
| 1 | source → tempFile (write) | source → tempFile (write) + checksum + header (tee) |
| 2 | virus scan (tempFile read) | virus scan (tempFile read) |
| 3 | checksum (tempFile read) | *(pass 1에서 완료)* |
| 4 | dedup 체크 | dedup 체크 (fast-path exit) |
| 5 | format (tempFile read) | format (header 버퍼) |
| 6 | encrypt (tempFile read → encryptedFile write) | 동일 |
| 7 | upload (encryptedFile read) | 동일 |

tempFile read 횟수: **4 → 2** (virus + encrypt)

---

## 5. 예외 계약

| 상황 | 예외 타입 | 발생 지점 | 비고 |
|------|---------|---------|------|
| source 읽기 실패 | `IOException` | pass 1 | 기존과 동일, tempFile finally 정리 |
| 바이러스 감염 | `FileStorageException(VIRUS_DETECTED)` | `scanForVirus` | 기존 시맨틱 유지 |
| format 추출 실패 | 구현체 예외 (일반적으로 `IOException` 또는 RuntimeException) | `formatExtractor.extract` | 기존 시맨틱 유지, header 버퍼 기반으로만 동작 |
| quota 초과 | `FileStorageException(QUOTA_EXCEEDED)` | `quotaChecker.check` | 기존 위치(encrypt 후) 유지 |
| encrypt 실패 | `FileStorageException(ENCRYPTION_FAILED)` | `encryptFile` | 기존 유지 |
| buffer size 검증 실패 | `IllegalArgumentException` | `Builder.formatHeaderBufferSize` 또는 `MagicByteBuffer` 생성자 | 신규 |

---

## 6. 테스트 매트릭스

### 6.1 `MagicByteBufferTest` (신규)

| # | 케이스 | 시나리오 | 검증 |
|---|-------|---------|------|
| M1 | default capacity | no-arg 생성자 | `capacity() == 16384` |
| M2 | custom capacity | `new MagicByteBuffer(4096)` | `capacity() == 4096`, `size() == 0` |
| M3 | capacity 미만 예외 | `new MagicByteBuffer(100)` | `IllegalArgumentException` |
| M4 | observe → size 증가 | 8 KiB 관찰 | `size() == 8192` |
| M5 | observe over capacity | 20 KiB 관찰 (16 KiB 용량) | `size() == 16384` (초과분 버림) |
| M6 | asInputStream 정확성 | 100 bytes 관찰 후 asInputStream.readAllBytes | 100 bytes 정확 일치 |
| M7 | empty | observe 없이 asInputStream | EOF 즉시 반환 |
| M8 | fragmented observe | `observe(a, 0, 4)`, `observe(b, 0, 4)` | 8 바이트 누적 |

### 6.2 `FileUploadServiceTest` 추가 케이스

| # | 케이스 | 검증 |
|---|-------|------|
| U1 | dedup hit → virus scan 수행됨 | Mockito `VirusScanner` `verify(scan(...), times(1))` |
| U2 | dedup hit → `formatExtractor` 미호출 | `verify(extract(...), never())` |
| U3 | dedup hit → `fileEncryptor.encrypt` 미호출 | `verify(encrypt(...), never())` |
| U4 | dedup hit → `storage.upload` 미호출 | `verify(upload(...), never())` |
| U5 | dedup miss → 기존 흐름 유지 (모든 단계 호출) | `verify(...)` 1회씩 |
| U6 | tempFile 읽기 횟수 (통합, 선택) | spy로 `Files.newInputStream(tempFile)` 호출 횟수 확인 |
| U7 | ingest 중 source IOException | tempFile 삭제 확인 |
| U8 | format 추출이 header 내 magic으로 정확 판정 | Tika 또는 stub extractor로 PNG/JPEG/PDF 검증 |
| U9 | formatHeaderBufferSize Builder 검증 | 0 또는 음수 → `IllegalArgumentException` |

### 6.3 기존 테스트 회귀

- `UploadDownloadIntegrationTest`: 현행 시나리오 유지 (암호화 + 업로드 + 다운로드 + 체크섬 검증)
- `EncryptionIntegrationTest`: 암호화 경로 기존 동작 유지
- `BatchUploadIntegrationTest`: dedup mix(일부 hit, 일부 miss) 배치

---

## 7. 구현 순서

| 단계 | 작업 | 파일 | 예상 |
|------|------|------|------|
| 1 | `MagicByteBuffer` + 단위 테스트 M1~M8 | io/ 신규 2개 | 40분 |
| 2 | `FileUploadService.Builder.formatHeaderBufferSize` 추가 + 검증 | upload/ 기존 수정 | 20분 |
| 3 | `doUpload()` 재작성 (ingest tee + 순서 재정렬) | upload/ 기존 | 60분 |
| 4 | `FileUploadServiceTest` U1~U9 추가 | test/upload | 60분 |
| 5 | 회귀 테스트 확인 + 통합 테스트 읽기 횟수 검증 | test/ 기존 | 40분 |
| 6 | JavaDoc 업데이트 + CHANGELOG `[Unreleased]` 엔트리 | 기존 | 20분 |

총 예상: **약 4시간**

---

## 8. 위험 재평가 (Plan §6 대비)

| 위험 | 완화 상태 |
|------|---------|
| 16 KiB로 판정 실패하는 포맷 | `Builder.formatHeaderBufferSize`로 조정 가능. 테스트에서 Tika 기본 동작 검증 |
| 레거시 `FileFormatExtractor`가 스트림 끝까지 소비 | header stream은 `ByteArrayInputStream` — 끝까지 읽어도 안전 (capacity만큼 반환). 단, **스트림 길이가 원본보다 짧다**는 문제는 남음. JavaDoc 명시 |
| dedup hit 시 virus 재스캔 비용 | 정책상 유지. 옵션화는 별건 |
| tee 중 예외 → tempFile 부분 쓰기 | finally에서 `Files.deleteIfExists` 실행 (기존 패턴 재사용) |
| Builder breaking | 기본값 16 KiB로 no-op 동작 보존 |

---

## 9. 공개 API 변경 요약

### 추가
- `io.github.dornol.filekit.io.MagicByteBuffer` (public final)
- `FileUploadService.Builder#formatHeaderBufferSize(int)`

### 변경
- `FileUploadService.doUpload()` 내부 경로 (공개 시그니처 불변)

### Breaking
- **미미(soft)**: 커스텀 `FileFormatExtractor` 구현체에 전달되는 InputStream은 이제 **앞 16 KiB만** 포함. 기본 `DefaultMediaTypeDetector`(Tika 기반)는 영향 없음. 마이그레이션 노트 제공

---

## 10. Next Steps

1. [ ] `/pdca do upload-pipeline-io` — §7 구현 순서대로 착수
2. [ ] 완료 후 `/pdca analyze upload-pipeline-io`
3. [ ] Match Rate ≥ 90% 시 `/pdca report upload-pipeline-io`

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
