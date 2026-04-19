# Plan: TempFileBuffer release() — DecryptionHelper 통합

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | tempbuffer-release |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `TempFileBuffer`에 `release()` 메서드 추가 + `DecryptionHelper`를 try-with-resources로 통일 |
| Related | R3 (temp-file-buffer 사이클) 후속. R3.1로 분류된 follow-up |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | R3에서 `TempFileBuffer`는 Upload/Transfer 2곳만 커버. `DecryptionHelper.java:36-55`는 여전히 수동 `Path decryptedFile = null` + catch 블록 내 suppressed 관리로 동일 보일러플레이트 존재 (성공 시 owner transfer 시맨틱 때문에 R3 범위에서 제외됨) |
| **Solution** | `TempFileBuffer.release()` 추가 — closed 플래그만 세팅하고 파일은 **남겨둠**, Path 반환. 호출자가 소유권을 이어받아 `DeleteOnCloseInputStream` 등에 위임 가능. DecryptionHelper를 try-with-resources로 리팩토링 |
| **Function/UX Effect** | DecryptionHelper LOC ~8줄 감소, 수동 null-check + catch-block-cleanup 제거. `TempFileBuffer`의 3번째 call-site 확보 |
| **Core Value** | R3에서 도입한 `TempFileBuffer` 패턴이 "단일 스코프 cleanup"뿐 아니라 "성공 시 소유권 이전" 시나리오까지 커버. 향후 유사 use case 표준화 |

---

## 1. 배경

### 1.1 `DecryptionHelper.java:36-55` 현황

```java
public static InputStream decryptToStream(InputStream encryptedContent, FileEncryptor fileEncryptor) {
    Path decryptedFile = null;
    try {
        decryptedFile = Files.createTempFile("file-kit-decrypted-", ".tmp");
        try (InputStream in = encryptedContent;
             OutputStream out = Files.newOutputStream(decryptedFile)) {
            fileEncryptor.decrypt(in, out);
        }
        return new DeleteOnCloseInputStream(decryptedFile);  // 성공 → 소유권 이전
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

**패턴 차이**:
- Upload/Transfer: temp file은 메서드 내에서 **모든 사용 종료** — try-with-resources가 자연스러움
- DecryptionHelper: **성공 시 temp file이 메서드 밖으로 나감** (`DeleteOnCloseInputStream`이 오너십 인수) — 기본 try-with-resources로는 close가 파일을 삭제해버림

### 1.2 해결 방식

`TempFileBuffer.release()` — 소유권 이양용 메서드:
- 호출 시 `closed = true`로 flag만 세팅 (파일 삭제 없음)
- 이후 try-with-resources의 암묵 `close()`는 no-op
- Path 반환 → 호출자가 다른 lifecycle(e.g. `DeleteOnCloseInputStream`)에 bind

---

## 2. 범위

### 2.1 In Scope

- [ ] `TempFileBuffer.release()` public 메서드 추가
- [ ] `TempFileBuffer` 단위 테스트 추가 (release 정상 / release 후 close no-op / double-release / release-after-close)
- [ ] `DecryptionHelper.decryptToStream` 리팩토링 — try-with-resources + release 사용
- [ ] JavaDoc 업데이트
- [ ] CHANGELOG

### 2.2 Out of Scope

- 다른 temp-file 생성 site (`LocalFileStorage.java:84` atomic-rename 패턴 — 완전 다른 목적)
- `DeleteOnCloseInputStream` 자체 변경

### 2.3 Design 단계 유보 결정

- **`release()` 시 이미 closed인 상태의 처리**: throw `IllegalStateException` vs silent return
- **double-release 처리**: throw vs silent (idempotent)
- **반환 타입**: `Path` vs `void` (path()로 별도 접근)

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | `release()` 호출 시 `close()`는 파일 삭제를 수행하지 않음 | High |
| FR-02 | `release()` 반환값은 underlying Path | High |
| FR-03 | 이미 closed 상태에서 `release()` → `IllegalStateException` (파일이 이미 지워졌으므로 의미 없음) | Medium |
| FR-04 | `release()` 2회 호출 → 2번째는 `IllegalStateException` | Medium |
| FR-05 | `release()` 후 `path()`는 여전히 동일 Path 반환 | Medium |
| FR-06 | `DecryptionHelper.decryptToStream` 정상 경로 시 temp 파일이 유지되고 `DeleteOnCloseInputStream`이 close 시 삭제 | High |
| FR-07 | `DecryptionHelper` 실패 경로 시 temp 파일 삭제 + `FileStorageException(DECRYPTION_FAILED)` 전파 | High |

### 3.2 비기능

- breaking 0 (신규 메서드만)
- 기존 `DecryptionHelperTest` 회귀 0

---

## 4. 설계 개요

### 4.1 `TempFileBuffer.release()`

```java
/**
 * Marks this buffer as "ownership transferred" without deleting the underlying
 * file. Subsequent {@link #close()} calls are no-ops. Intended for cases where
 * the temp file must outlive the try-with-resources block (e.g. to be owned
 * by a DeleteOnCloseInputStream).
 *
 * @return the underlying path
 * @throws IllegalStateException if already closed or already released
 */
public Path release() { ... }
```

### 4.2 DecryptionHelper 재작성

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

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `TempFileBuffer.release()` + 단위 테스트 | 30분 |
| 2 | `DecryptionHelper` 리팩토링 | 15분 |
| 3 | `DecryptionHelperTest` 회귀 확인 | 10분 |
| 4 | JavaDoc + CHANGELOG | 10분 |

총 예상: **약 1시간**

---

## 6. 공개 API 변경

### 추가
- `TempFileBuffer.release()` public method

### 변경
- `DecryptionHelper.decryptToStream` 내부 구조 (공개 시그니처 불변)

### Breaking
- 없음

---

## 7. Next Steps

1. [ ] `/pdca design tempbuffer-release`
2. [ ] 구현 → Check → Report

---

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
