# Design: Streaming Checksum Verification on Download

> **Summary**: 다운로드 체크섬 검증을 `readAllBytes()` 기반에서 스트리밍 디지스트로 전환
>
> **Project**: file-kit
> **Version**: 0.1.10 → 0.1.11 (예정)
> **Author**: dhkim
> **Date**: 2026-04-19
> **Status**: Draft
> **Plan**: [streaming-checksum-verify.plan.md](../../01-plan/features/streaming-checksum-verify.plan.md)

---

## 1. 설계 목표

- `FileDownloadService.verifyChecksum`의 메모리 O(파일) 의존 제거
- `ChecksumCalculator` SPI를 깨지 않고 증분 계산 확장
- 기존 테스트 회귀 0, 신규 래퍼/SPI 단위 커버리지 ≥ 90%
- 원칙: **CLAUDE.md — JDK 표준으로 충분한 것은 만들지 않는다**. `MessageDigest`는 JDK이지만 소비자가 직접 엮어야 하는 보일러플레이트(다이제스트·비교·스트림 래핑)가 있어 file-kit이 감싼다.

### 설계 원칙

- 최소 API surface: 신규 공개 타입 2개 (`ChecksumComputation`, `ChecksumVerifyingInputStream`), 신규 공개 메서드 1개 (`ChecksumCalculator#newComputation`)
- 하위 호환: `newComputation()`은 default method, 폴백 구현 내장
- 관찰 가능성: early-close는 에러가 아닌 `log.warn`으로 가시화 (오탐 방지)
- 결함 우선순위: decrypt 실패 → virus/quota 의미적 예외는 본 피처 범위 밖이나 wrapping 순서만 계약 고정

---

## 2. 컴포넌트 구조

### 2.1 클래스 관계

```
┌──────────────────────────┐
│ FileDownloadService      │
│  - checksumCalculator    │
│  verifyChecksum(...)     │──uses──▶┌────────────────────────────┐
└──────────────────────────┘         │ ChecksumVerifyingInputStream│
                                     │ extends FilterInputStream   │
                                     │  - computation              │
                                     │  - expected, fileKey        │
                                     │  - state                    │
                                     └────────────┬────────────────┘
                                                  │ delegates update/finish
                                                  ▼
                       ┌──────────────────────────────────────────┐
                       │ ChecksumComputation (interface, new SPI) │
                       │  update(byte[], int, int)                │
                       │  finish(): String                        │
                       └──────────┬────────────────────┬──────────┘
                                  │                    │
                    ┌─────────────┘                    └──────────────┐
                    ▼                                                 ▼
      ┌────────────────────────────┐              ┌───────────────────────────────┐
      │ MessageDigestComputation    │              │ BufferingComputation          │
      │ (inner in Sha256...)        │              │ (package-private fallback)    │
      │ wraps MessageDigest         │              │ buffers to ByteArrayOutputStream│
      └────────────────────────────┘              └───────────────────────────────┘
                    ▲                                             ▲
                    │ newComputation() override                   │ newComputation() default
                    │                                             │
      ┌────────────────────────────┐              ┌───────────────────────────────┐
      │ Sha256ChecksumCalculator    │              │ 커스텀 ChecksumCalculator 구현체 │
      │ implements ChecksumCalculator│              │ (미override 시 fallback 사용)   │
      └────────────────────────────┘              └───────────────────────────────┘
```

### 2.2 파일 위치

| 분류 | 파일 | 공개 |
|------|------|------|
| 신규 SPI | `kit-core/src/main/java/io/github/dornol/filekit/spi/ChecksumComputation.java` | public |
| 신규 fallback | `kit-core/src/main/java/io/github/dornol/filekit/spi/BufferingComputation.java` | package-private |
| SPI default 추가 | `kit-core/.../spi/ChecksumCalculator.java` | 기존 파일 수정 |
| Sha256 override | `kit-core/.../spi/Sha256ChecksumCalculator.java` | 기존 파일 수정 |
| 검증 래퍼 | `kit-core/src/main/java/io/github/dornol/filekit/io/ChecksumVerifyingInputStream.java` | public |
| 서비스 교체 | `kit-core/.../download/FileDownloadService.java` (verifyChecksum 메서드만) | 기존 수정 |

---

## 3. API 정의

### 3.1 `ChecksumComputation` (신규)

```java
package io.github.dornol.filekit.spi;

/**
 * Incremental checksum computation for streaming use.
 *
 * <p>Instances are <b>stateful</b> and <b>not thread-safe</b>.
 * Obtain a new instance via {@link ChecksumCalculator#newComputation()} for each computation.</p>
 *
 * <p>Typical lifecycle:
 * <pre>{@code
 * ChecksumComputation c = calc.newComputation();
 * while ((n = in.read(buf)) != -1) c.update(buf, 0, n);
 * String checksum = c.finish();
 * }</pre>
 *
 * <p>After {@link #finish()} is called, subsequent {@code update} or {@code finish} calls
 * result in {@link IllegalStateException}.</p>
 */
public interface ChecksumComputation {

    /**
     * Updates the computation with a portion of a byte buffer.
     *
     * @throws IllegalStateException if {@link #finish()} was already called
     */
    void update(byte[] buf, int off, int len);

    /**
     * Finalizes the computation and returns the checksum string.
     *
     * @throws IllegalStateException if called more than once
     */
    String finish();
}
```

### 3.2 `ChecksumCalculator#newComputation` (기존 SPI 확장)

```java
// 기존 인터페이스에 default method 추가 (시그니처 변경 없음)

/**
 * Returns a new incremental computation.
 *
 * <p>The default implementation buffers all updates into memory and delegates
 * to {@link #checksum(byte[])} on {@link ChecksumComputation#finish()}, which
 * preserves backward compatibility but loses the streaming benefit. Override
 * this method to provide a true streaming implementation.</p>
 *
 * @since 0.1.11
 */
default ChecksumComputation newComputation() {
    return new BufferingComputation(this);
}
```

### 3.3 `BufferingComputation` (package-private fallback)

```java
package io.github.dornol.filekit.spi;

import java.io.ByteArrayOutputStream;

final class BufferingComputation implements ChecksumComputation {
    private final ChecksumCalculator delegate;
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    BufferingComputation(ChecksumCalculator delegate) { this.delegate = delegate; }

    @Override public void update(byte[] buf, int off, int len) {
        if (buffer == null) throw new IllegalStateException("already finished");
        buffer.write(buf, off, len);
    }

    @Override public String finish() {
        if (buffer == null) throw new IllegalStateException("already finished");
        String result = delegate.checksum(buffer.toByteArray());
        buffer = null;
        return result;
    }
}
```

### 3.4 `Sha256ChecksumCalculator` override

```java
@Override
public ChecksumComputation newComputation() {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return new ChecksumComputation() {
            private boolean finished = false;
            @Override public void update(byte[] buf, int off, int len) {
                if (finished) throw new IllegalStateException("already finished");
                md.update(buf, off, len);
            }
            @Override public String finish() {
                if (finished) throw new IllegalStateException("already finished");
                finished = true;
                return HexFormat.of().formatHex(md.digest());
            }
        };
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 not available", e);
    }
}
```

### 3.5 `ChecksumVerifyingInputStream` (신규)

**상태 머신**

```
       ┌─────────┐    read() normal    ┌──────────┐
 init→ │ READING │────────────────────▶│ READING  │  (update 호출)
       └────┬────┘                     └────┬─────┘
            │ read()==-1                    │ close() before EOF
            ▼                               ▼
      ┌──────────────┐               ┌──────────────┐
      │ VERIFYING    │               │ EARLY_CLOSED │  (WARN log, no compare)
      │ (compute →   │               └──────────────┘
      │  compare)    │                       │
      └──────┬───────┘                       │ close()
             │                                ▼
     ┌───────┴────────┐                  (underlying close)
     │                │
  MATCH           MISMATCH
  (returns -1)   (throw FileStorageException)
     │                │
     ▼                ▼
 ┌────────┐     ┌─────────┐
 │VERIFIED│     │ FAILED  │
 └────────┘     └─────────┘
     │                │
     └──┬───close()───┘
        ▼
 (underlying close, no-op on verified state)
```

**메서드 시그니처 계약**

| 메서드 | 동작 | 비고 |
|-------|------|------|
| `read()` | super.read() → -1이면 `verify()`, 아니면 1바이트 `update` | 핫 패스. single-byte buffer 재사용 |
| `read(byte[] b, int off, int len)` | super.read() → -1이면 `verify()`, n>0이면 `update(b, off, n)` | 주요 경로 |
| `read(byte[] b)` | `InputStream` 기본 구현이 `read(b,0,b.length)` 호출 → 위 경로 재사용 | override 불필요 |
| `skip(long n)` | `throw new UnsupportedOperationException` | 디지스트 누락 방지. JavaDoc 명시 |
| `available()` | `super.available()` 그대로 전달 | 서블릿 호환 |
| `mark(int), reset()` | `mark` no-op, `reset` → `IOException("mark/reset not supported")` | |
| `markSupported()` | `false` | |
| `close()` | state가 READING이면 WARN log + state = EARLY_CLOSED. 이후 `super.close()` (try-finally) | 검증 실패가 close에서 던져지지 않음 |

**주요 불변식**

- `verify()`는 EOF 도달 시 정확히 한 번만 호출 (idempotent 가드)
- `close()` 호출 시, verify에서 이미 예외가 던져졌더라도 underlying stream close 시도 (try-finally)
- `read()`에서 예외가 발생하는 경우, 호출자는 스트림을 닫아야 함 (JavaDoc 명시)
- 단일 인스턴스를 여러 스레드에서 read 하면 안 됨 (JavaDoc 명시)

**클래스 스케치**

```java
public final class ChecksumVerifyingInputStream extends FilterInputStream {

    private final ChecksumComputation computation;
    private final String expected;
    private final String fileKey;
    private final byte[] singleByteBuf = new byte[1];
    private boolean verified = false;
    private boolean earlyClosed = false;

    public ChecksumVerifyingInputStream(InputStream in,
                                        ChecksumComputation computation,
                                        String expected,
                                        String fileKey) {
        super(Objects.requireNonNull(in, "in"));
        this.computation = Objects.requireNonNull(computation, "computation");
        this.expected = Objects.requireNonNull(expected, "expected");
        this.fileKey = Objects.requireNonNull(fileKey, "fileKey");
    }

    @Override public int read() throws IOException {
        int v = super.read();
        if (v == -1) verify();
        else { singleByteBuf[0] = (byte) v; computation.update(singleByteBuf, 0, 1); }
        return v;
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) verify();
        else if (n > 0) computation.update(b, off, n);
        return n;
    }

    @Override public long skip(long n) {
        throw new UnsupportedOperationException("skip() would break checksum verification");
    }

    @Override public boolean markSupported() { return false; }
    @Override public synchronized void mark(int readlimit) { /* no-op */ }
    @Override public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported by ChecksumVerifyingInputStream");
    }

    @Override public void close() throws IOException {
        try {
            if (!verified && !earlyClosed) {
                earlyClosed = true;
                log.warn("Checksum verification skipped (stream closed before EOF): key={}", fileKey);
            }
        } finally {
            super.close();
        }
    }

    private void verify() {
        if (verified) return;
        verified = true;
        String actual = computation.finish();
        if (!expected.equals(actual)) {
            throw new FileStorageException(FileStorageException.CHECKSUM_MISMATCH,
                "Checksum mismatch for key=" + fileKey
                    + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
```

---

## 4. 예외 계약

### 4.1 예외 발생 경로

| 상황 | 예외 타입 | 발생 지점 | 비고 |
|------|---------|---------|------|
| 디지스트 불일치 | `FileStorageException(CHECKSUM_MISMATCH)` | `read()`가 -1 반환하는 호출 내부 | 기존과 동일 에러 코드 |
| underlying IOException | `IOException` | `read()` | 투명 전파 |
| decrypt 실패 | 기존 decrypt 예외 | verify.read → decrypt.read 내부에서 발생, verify까지 도달 안 함 | 합성 래핑 순서 덕에 자연 해결 |
| close 중 IOException | `IOException` | `close()` | verify 관련 예외는 close에서 던지지 않음 |
| `skip(long)` 호출 | `UnsupportedOperationException` | 즉시 | 문서에 명시 |

### 4.2 합성 래핑 순서 (기존 유지)

```
원시 storage stream  ◀ (가장 안쪽)
  ↑
DecryptingInputStream (encryptor enabled 시)
  ↑
ChecksumVerifyingInputStream (calculator 설정 시)  ◀ 가장 바깥쪽
  ↑
사용자 호출 (DownloadResult#content)
```

- 순서 불변: decrypt가 항상 verify보다 먼저 파이프라인에 들어감 → 복호화 성공한 평문 바이트 기준으로 디지스트 계산 (기존 의미 보존)

---

## 5. FileDownloadService 변경

**Before (L179-195):**
```java
private InputStream verifyChecksum(InputStream content, FileMetadata metadata) {
    try {
        byte[] bytes = content.readAllBytes();        // ← O(file) heap
        String actual = checksumCalculator.checksum(bytes);
        if (!actual.equals(metadata.checksum())) { throw ...; }
        return new ByteArrayInputStream(bytes);
    } catch (IOException e) { ... }
}
```

**After:**
```java
private InputStream verifyChecksum(InputStream content, FileMetadata metadata) {
    return new ChecksumVerifyingInputStream(
        content,
        checksumCalculator.newComputation(),
        metadata.checksum(),
        metadata.key()
    );
}
```

JavaDoc 교체 (L122-130 `download()` 메서드):

```
If a ChecksumCalculator was configured, the returned InputStream transparently
verifies the checksum while being read. The verification completes when the
consumer reads to EOF; if verification fails, a FileStorageException is thrown
from the read() call that returns -1. Closing the stream before EOF skips
verification (WARN logged).
```

---

## 6. 테스트 매트릭스

### 6.1 `ChecksumVerifyingInputStreamTest` (신규)

| # | 케이스 | 시나리오 | 검증 |
|---|-------|---------|------|
| T1 | happy path — single read loop | 1KB 정상 스트림, byte[] 버퍼로 읽기 | 정상 완료, 예외 없음 |
| T2 | happy path — byte-by-byte | read()로 1바이트씩 | 정상 완료 |
| T3 | mismatch at EOF | 마지막 바이트만 변조 | CHECKSUM_MISMATCH, read가 -1 반환 대신 예외 |
| T4 | mismatch mid-stream | 중간 바이트 변조 | EOF 전엔 예외 없음, EOF에서 예외 |
| T5 | early close before EOF | 절반만 읽고 close | 예외 없음, WARN 로그 발생 |
| T6 | close after EOF | 정상 검증 후 close | no-op, 예외 없음 |
| T7 | close while verify throws | verify에서 예외 던지는 경로에서 close 호출 | underlying stream close는 호출됨 |
| T8 | skip() | any skip call | UnsupportedOperationException |
| T9 | mark/reset | markSupported false, reset IOException | 계약 검증 |
| T10 | double-finish guard | verify 2회 호출 | 두 번째는 no-op |
| T11 | read on underlying IOException | 하위 스트림이 IOException 던짐 | 투명 전파, verified=false |
| T12 | empty stream | 0바이트 스트림, 디지스트=empty SHA-256 | 정상 검증 또는 mismatch |

### 6.2 `ChecksumCalculatorNewComputationTest` (신규)

| # | 케이스 | 검증 |
|---|-------|------|
| N1 | Sha256 override가 `MessageDigest` 기반 동작 | buffering 없이 동일 해시 |
| N2 | 커스텀 구현체 default 사용 | `BufferingComputation`으로 폴백, checksum 값 일치 |
| N3 | `BufferingComputation` double finish | IllegalStateException |
| N4 | `Sha256...newComputation` 인스턴스 분리 | 두 인스턴스가 독립 상태 |

### 6.3 `FileDownloadServiceTest` (기존 파일 수정)

- 기존 검증 관련 테스트를 신규 래퍼 기반으로 마이그레이션
- 새 케이스: "verify 설정 상태에서 download 반환 스트림이 `ChecksumVerifyingInputStream`의 행동 계약을 만족" (간접)

### 6.4 `UploadDownloadIntegrationTest` (기존 파일 추가)

| # | 케이스 | 검증 |
|---|-------|------|
| I1 | 100MB 업로드 → 체크섬 설정된 서비스로 download → 전체 read | 정상 완료, 힙 모니터로 `readAllBytes` 대비 메모리 감소 확인 (JMX 선택) |
| I2 | 1GB (sparse)* 다운로드 → 전체 read | OOM 없이 완료 |
| I3 | 암호화+체크섬 복합 다운로드 → decrypt → verify 순서 | 해시 일치, 예외 없음 |
| I4 | 저장된 디지스트와 스토리지 상 파일 변조 시뮬레이션 | CHECKSUM_MISMATCH |

*I2: 실제 1GB 파일 생성 대신 메모리에 sparse 바이트 제너레이터로 스트림 구성 → CI 부하 억제

---

## 7. 구현 순서 (예상 공수)

| 단계 | 작업 | 파일 | 예상 |
|------|------|------|------|
| 1 | `ChecksumComputation` + `BufferingComputation` | spi/ 신규 2개 | 20분 |
| 2 | `ChecksumCalculator#newComputation` default 추가 | spi/ChecksumCalculator.java | 10분 |
| 3 | `Sha256ChecksumCalculator` override | spi/Sha256ChecksumCalculator.java | 20분 |
| 4 | `ChecksumVerifyingInputStream` | io/ 신규 | 40분 |
| 5 | 단위 테스트 T1~T12, N1~N4 | test/ 신규 | 60분 |
| 6 | `FileDownloadService.verifyChecksum` 교체 + JavaDoc | download/FileDownloadService.java | 20분 |
| 7 | 기존 Download 테스트 회귀 확인 | test/download | 20분 |
| 8 | 통합 테스트 I1~I4 | test/integration | 60분 |
| 9 | CHANGELOG 엔트리 + README 체크섬 섹션 업데이트 | CHANGELOG.md, README.md | 20분 |

총 예상: **약 4~5시간** (테스트 밀도 포함)

---

## 8. 미해결 결정사항

본 Design 단계에서 다음은 **확정**:

- ✅ `skip()` 허용 안 함 → `UnsupportedOperationException` (T8)
- ✅ `early close` → WARN 로그 + 검증 스킵 (엄격 모드 없음)
- ✅ Range 검증 → 범위 밖 (현 서비스에 downloadRange API 없음)
- ✅ decrypt + verify 순서 → 기존 유지 (verify outer, decrypt inner)
- ✅ `BufferingComputation`은 package-private (공개 SPI 오염 방지)

다음 단계에서 다룰 항목:

- ⏸ I2 통합 테스트의 정확한 "sparse 제너레이터" 구현 방식 (Do 단계에서 결정)
- ⏸ CHANGELOG 엔트리 문구 (Do 단계 마지막에 작성)

---

## 9. Next Steps

1. [ ] `/pdca do streaming-checksum-verify` — 구현 시작
2. [ ] 구현 완료 후 `/pdca analyze streaming-checksum-verify` — Gap 검증
3. [ ] Match Rate ≥ 90% 도달 시 `/pdca report streaming-checksum-verify`

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
