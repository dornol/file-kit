# Design: TempFileBuffer release() — DecryptionHelper 통합

> **Plan**: [tempbuffer-release.plan.md](../../01-plan/features/tempbuffer-release.plan.md)
> **Status**: Draft · 2026-04-19

---

## 1. 유보 결정 확정 (Plan §2.3)

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 이미 closed 상태에서 `release()` | **`IllegalStateException`** | 파일이 이미 삭제됨 — 반환받은 Path로 I/O 시도 시 ENOENT. 조용히 path 반환하면 디버깅 어려움. fail-fast |
| double-release | **`IllegalStateException`** | 소유권 이전은 1회성 계약. 두 번째 호출자는 허상 Path 받음 → 버그 |
| 반환 타입 | **`Path`** | release + getPath 2-step 대신 1-call로 깔끔. 사용처(`new DeleteOnCloseInputStream(buf.release())`)도 1줄 |

### 1.1 상태 전이

```
INITIAL ─── release() ──▶ RELEASED (close() no-op, release() throws)
   │
   └── close() ────────▶ CLOSED   (release() throws, close() no-op)
```

`closed` 단일 플래그 재사용. 추가 플래그 없이 상태 구분:
- INITIAL: `closed == false`, 파일 존재
- CLOSED: `closed == true`, 파일 삭제 시도됨
- RELEASED: `closed == true`, 파일 남아있음 (호출자 소유)

→ `release()`와 `close()`는 모두 `closed` 체크로 분기. `release()` 내부: `if (closed) throw`, 아니면 `closed = true; return path;`

---

## 2. API 명세

### 2.1 `TempFileBuffer.release()`

```java
/**
 * Transfers ownership of the underlying file to the caller.
 *
 * <p>After this call, {@link #close()} becomes a no-op — the file is
 * <b>not</b> deleted on try-with-resources exit. The caller takes over
 * the file's lifecycle.
 *
 * <p>Intended for cases where the temp file must outlive the
 * {@code try-with-resources} block, such as wrapping it into a
 * {@link DeleteOnCloseInputStream} that deletes on stream close.
 *
 * <p>Usage:
 * <pre>{@code
 * try (TempFileBuffer buf = TempFileBuffer.create("prefix-")) {
 *     // ... write into buf.path() ...
 *     return new DeleteOnCloseInputStream(buf.release());
 * }
 * // On exception here, buf.close() fires and deletes the file
 * // (because release() was not reached).
 * }</pre>
 *
 * @return the underlying {@link Path} — same instance as {@link #path()}
 * @throws IllegalStateException if this buffer is already closed or released
 * @since 0.1.15
 */
public Path release() {
    if (closed) {
        throw new IllegalStateException("TempFileBuffer already closed or released");
    }
    closed = true;
    return path;
}
```

### 2.2 기존 `close()` 동작은 변경 없음

`close()` 내부의 `if (closed) return;` 체크가 release 후에도 자연스럽게 동작. 추가 분기 불필요.

---

## 3. DecryptionHelper 재작성

### 3.1 Before (L36-55)

```java
public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor) {
    Path decryptedFile = null;
    try {
        decryptedFile = Files.createTempFile("file-kit-decrypted-", ".tmp");
        try (InputStream in = encryptedContent;
             OutputStream out = Files.newOutputStream(decryptedFile)) {
            fileEncryptor.decrypt(in, out);
        }
        return new DeleteOnCloseInputStream(decryptedFile);
    } catch (IOException e) {
        if (decryptedFile != null) {
            try {
                Files.deleteIfExists(decryptedFile);
            } catch (IOException deleteEx) {
                e.addSuppressed(deleteEx);
            }
        }
        throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                "Failed to decrypt file content", e);
    }
}
```

### 3.2 After

```java
public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor) {
    try (TempFileBuffer buf = TempFileBuffer.create("file-kit-decrypted-")) {
        try (InputStream in = encryptedContent;
             OutputStream out = Files.newOutputStream(buf.path())) {
            fileEncryptor.decrypt(in, out);
        }
        return new DeleteOnCloseInputStream(buf.release());
    } catch (IOException e) {
        throw new FileStorageException(FileStorageException.DECRYPTION_FAILED,
                "Failed to decrypt file content", e);
    }
}
```

### 3.3 시맨틱 차이 검증

| 경로 | Before | After | 동일? |
|------|--------|-------|:---:|
| 정상 (decrypt 성공) | 파일 생성 → decrypt → `DeleteOnCloseInputStream`에 Path 전달 | 동일 (release로 ownership 이전) | ✅ |
| decrypt 중 IOException | catch → `Files.deleteIfExists(decryptedFile)` → suppressed on delete fail → throw DECRYPTION_FAILED | release 호출 전 예외 → TWR close가 `Files.deleteIfExists` (swallow + WARN) → throw DECRYPTION_FAILED | ✅ (cleanup 보장) |
| createTempFile 실패 | `Path decryptedFile = null` → catch 블록에서 null 체크로 스킵 | `TempFileBuffer.create` 자체가 IOException → catch 블록에서 TWR 없이 바로 예외 처리 | ✅ |
| close 중 `Files.deleteIfExists` 실패 | Before는 catch 블록 내부에서 `e.addSuppressed(deleteEx)` | After는 `TempFileBuffer.close`가 WARN 로그만 수행, suppressed 기록 안 함 | 🟡 **미묘한 차이** |

### 3.4 🟡 미묘한 차이에 대한 결정

**Before**: decrypt 실패 → temp file 삭제 시도 → delete 실패 시 `DECRYPTION_FAILED` 예외의 suppressed에 delete 예외 기록
**After**: TempFileBuffer.close는 WARN 로그만, suppressed 기록 안 함

- **영향**: `getSuppressed()`로 cleanup 실패를 관찰하던 앱이 있다면 정보 손실. 단, 이 API는 기존에도 `e.addSuppressed` 후 `throw new FileStorageException(...e)` 순서 — suppressed는 원인 예외(`e`)의 suppressed이지 wrapped `FileStorageException`의 suppressed가 아님. 실제로 앱이 `throw FileStorageException`의 getSuppressed를 보면 비어있었음. 따라서 **관찰 가능한 변화 없음**
- **로그**: WARN 로그는 여전히 남음 (TempFileBuffer.close 내부)
- **결론**: 시맨틱 동등 (관찰 가능한 계약 변화 0)

---

## 4. 테스트 매트릭스

### 4.1 `TempFileBufferTest` 신규 케이스

| # | 케이스 | 검증 |
|---|-------|------|
| R1 | `release()` 반환값은 `path()`와 동일 | assertSame |
| R2 | release 후 TWR 종료 시 파일 유지 | `Files.exists` true |
| R3 | release 후 명시적 `close()` 호출 — no-op | 파일 여전히 존재 |
| R4 | double-release → `IllegalStateException` | assertThrows |
| R5 | close 후 release → `IllegalStateException` | assertThrows |
| R6 | release 후 release (TWR 종료 포함) 시 cleanup은 호출자 책임 | 테스트 끝나고 수동 delete 검증 |

### 4.2 `DecryptionHelperTest` 회귀

- 정상 decrypt → 반환 스트림 read 후 close → temp 파일 삭제 확인
- decrypt 중 IOException → 예외 전파 + temp 파일 삭제 확인
- createTempFile 자체 실패 (mock 어려움, skip)

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `TempFileBuffer.release()` + R1~R5 테스트 | 25분 |
| 2 | `DecryptionHelper` 리팩토링 | 10분 |
| 3 | 회귀 테스트 (`DecryptionHelperTest`, 전체 suite) | 15분 |
| 4 | JavaDoc + CHANGELOG | 10분 |

총 예상: **약 1시간**

---

## 6. 공개 API

### 추가
- `TempFileBuffer.release()` (`@since 0.1.15`)

### Breaking
- 없음

---

## 7. Next Steps

1. [ ] `/pdca do tempbuffer-release`

---

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
