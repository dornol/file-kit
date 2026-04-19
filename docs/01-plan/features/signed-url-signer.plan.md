# Plan: SignedUrlSigner HMAC Helper

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | signed-url-signer |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `SignedUrlSigner` 유틸 (HMAC-SHA256 기반 URL 서명/검증). 인가 자체는 앱 책임 유지 |
| Related | 초기 리뷰 A8 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | Local 스토리지 + HTTP 서버로 파일 서빙하는 앱이 "만료되는 위조 불가능 URL"을 발급하려면 HMAC 서명·timestamp·constant-time 비교를 매번 직접 구현. 암호 실수 위험 (timing attack, weak MAC, 서명 payload 정의 미흡) |
| **Solution** | `SignedUrlSigner(secret)` — `sign(fileKey, expiration)`으로 query 조각 발급, `verify(fileKey, exp, sig)`로 검증. HMAC-SHA256 + constant-time 비교 + URL-safe Base64 표준 조합 |
| **Function/UX Effect** | 1줄로 signed URL 생성/검증. `SignedUrlExpiredException` / `SignedUrlInvalidSignatureException`으로 분기 명확 |
| **Core Value** | 파일 서빙 endpoint 구현 부담 감소. 인가는 여전히 앱 책임 (서명 통과 후 앱이 별도 체크) — CLAUDE.md 원칙 보존 |

---

## 1. 배경

### 1.1 현재 한계

- `FileStorage.generatePresignedUrl(...)` (`storage/FileStorage.java:61`) default는 `UnsupportedOperationException`
- `LocalFileStorage`는 override 안 함 → Local 파일은 presigned URL 불가
- S3/GCS는 자체 구현체에서 override하면 됨

### 1.2 범위 선 긋기

file-kit이 **하는 것**: HMAC 서명·검증의 암호 연산 파트만
file-kit이 **안 하는 것**:
- URL base (`https://my-server/...`) 조립 — 앱 결정
- 인가 (user X가 file Y 권한?) — 앱 책임
- `LocalFileStorage.generatePresignedUrl` 자동 구현 — HTTP layer 앱마다 다름

### 1.3 사용 흐름 (예시)

```java
// 앱 startup
SignedUrlSigner signer = new SignedUrlSigner(secretBytes);

// 발급 (생성 endpoint)
String query = signer.sign(fileMeta.key(), Instant.now().plus(Duration.ofHours(1)));
return "https://files.myapp.com/download?key=" + fileMeta.key() + "&" + query;

// 서빙 (download endpoint)
try {
    signer.verify(req.getParam("key"), req.getParam("exp"), req.getParam("sig"));
    // 앱이 별도 인가 체크 (user X가 file Y 권한?)
    return downloadService.download(req.getParam("key"));
} catch (SignedUrlExpiredException e) {
    return 410_GONE;
} catch (SignedUrlInvalidSignatureException e) {
    return 403_FORBIDDEN;
}
```

---

## 2. 범위

### 2.1 In Scope

- [ ] 새 패키지 `io.github.dornol.filekit.url`
- [ ] `SignedUrlSigner` (public final) — HMAC-SHA256 기반
  - `sign(String fileKey, Instant expiration)` → `String` (형식: `"exp={epochSec}&sig={base64url}"`)
  - `verify(String fileKey, long exp, String sigBase64)` — 만료/위조 예외
  - `verify(String fileKey, String queryFragment)` — 편의 오버로드
- [ ] `SignedUrlExpiredException`, `SignedUrlInvalidSignatureException` (public, 공통 `SignedUrlException` 베이스)
- [ ] `Clock` 주입 가능 (testability)
- [ ] 단위 테스트 ~10 케이스
- [ ] CHANGELOG

### 2.2 Out of Scope

- `LocalFileStorage.generatePresignedUrl` 구현 (HTTP 서빙은 앱마다 다름)
- URL base/path assembly
- Signature version rotation
- JWT / JWS — 과잉, 이 유스케이스에선 불필요

### 2.3 유보 결정

- **query format**: `"exp=...&sig=..."` 단일 문자열 vs `record(String exp, String sig)`
- **HMAC 키 최소 길이**: 16바이트 vs 32바이트 강제 검증 vs 경고
- **secret 저장**: `byte[]` vs `SecretKeySpec` (소비자 선택)
- **Base64 variant**: URL-safe (+ no padding) vs standard
- **서명 payload 포맷**: `fileKey + "|" + exp` vs `length-prefixed` vs `canonicalized`

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | HMAC-SHA256 서명 + URL-safe Base64 인코딩 | High |
| FR-02 | 만료 비교는 주입된 `Clock.instant()` 기준 | High |
| FR-03 | 서명 비교는 `MessageDigest.isEqual(byte[], byte[])` (constant-time) | High |
| FR-04 | `sig` 디코딩 실패 → `SignedUrlInvalidSignatureException` (timing/error hiding) | High |
| FR-05 | 만료됨 → `SignedUrlExpiredException` | High |
| FR-06 | null/blank 입력 → `NullPointerException` or `IllegalArgumentException` | Medium |
| FR-07 | secret 바이트 `< 16` → `IllegalArgumentException` | Medium |
| FR-08 | `sign` → `verify` 왕복 성공 (same instance + same clock) | High |

---

## 4. 구현 설계

### 4.1 `SignedUrlSigner`

```java
public final class SignedUrlSigner {
    private static final String ALGO = "HmacSHA256";
    private static final int MIN_SECRET_LEN = 16;
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final SecretKey secretKey;
    private final Clock clock;

    public SignedUrlSigner(byte[] secret) {
        this(secret, Clock.systemUTC());
    }

    public SignedUrlSigner(byte[] secret, Clock clock) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < MIN_SECRET_LEN) {
            throw new IllegalArgumentException(
                    "secret must be at least " + MIN_SECRET_LEN + " bytes, got " + secret.length);
        }
        this.secretKey = new SecretKeySpec(secret, ALGO);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public String sign(String fileKey, Instant expiration) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(expiration, "expiration");
        long exp = expiration.getEpochSecond();
        byte[] sig = hmac(fileKey + "|" + exp);
        return "exp=" + exp + "&sig=" + ENC.encodeToString(sig);
    }

    public void verify(String fileKey, long exp, String sigBase64) {
        Objects.requireNonNull(fileKey, "fileKey");
        Objects.requireNonNull(sigBase64, "sigBase64");
        if (Instant.ofEpochSecond(exp).isBefore(clock.instant())) {
            throw new SignedUrlExpiredException("signed URL expired at " + exp);
        }
        byte[] expected = hmac(fileKey + "|" + exp);
        byte[] actual;
        try {
            actual = DEC.decode(sigBase64);
        } catch (IllegalArgumentException e) {
            throw new SignedUrlInvalidSignatureException("malformed signature");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SignedUrlInvalidSignatureException("signature mismatch");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(secretKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
```

### 4.2 예외 계층

```java
public class SignedUrlException extends RuntimeException {
    public SignedUrlException(String message) { super(message); }
}

public class SignedUrlExpiredException extends SignedUrlException {
    public SignedUrlExpiredException(String message) { super(message); }
}

public class SignedUrlInvalidSignatureException extends SignedUrlException {
    public SignedUrlInvalidSignatureException(String message) { super(message); }
}
```

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | 예외 3개 + `SignedUrlSigner` + 단위 테스트 ~10건 | 60분 |
| 2 | 회귀 + 빌드 | 10분 |
| 3 | CHANGELOG | 10분 |

총: **약 1.5시간**

---

## 6. 공개 API

### 추가
- `io.github.dornol.filekit.url.SignedUrlSigner`
- `io.github.dornol.filekit.url.SignedUrlException` (base)
- `io.github.dornol.filekit.url.SignedUrlExpiredException`
- `io.github.dornol.filekit.url.SignedUrlInvalidSignatureException`

### Breaking
- 없음

---

# Design

## 7. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| query format | **단일 `"exp=...&sig=..."` 문자열** | 발급 즉시 URL에 append 가능. Parsing 편의는 `verify(fileKey, queryFragment)` 오버로드 별도 제공 검토 가능하나 YAGNI |
| HMAC 키 최소 길이 | **16 바이트 강제 (IllegalArgumentException)** | NIST SP 800-117 HMAC-SHA256 최소 128-bit 권장. 실수 방지 |
| secret 저장 | **`byte[]` 입력 → 내부 `SecretKeySpec`** | `byte[]`는 보편적, SecretKeySpec은 내부 구현 디테일 |
| Base64 variant | **URL-safe + no padding** | URL에 직접 append 가능 |
| 서명 payload | **`fileKey + "\|" + exp`** (pipe separator) | 단순. fileKey에 `\|` 금지는 비현실적이지만 파일 키는 UUID 관례라 문제 없음. 필요시 문서화 |
| `verify` 편의 오버로드 (`String queryFragment`) | **Phase 2 피처로 유보** | 1차 기능은 `(fileKey, long exp, String sig)` 명시 파라미터. 파싱은 호출자 몫 (`exp=`/`sig=` 추출) |
| 패키지 | **`url`** (kit-core 내 신규 sub-package) | 스토리지/download와 독립. 명확한 이름 |
| secret zero-out | **구현 안 함** | Java `byte[]`는 GC, `SecretKeySpec`도 명시적 `destroy`는 선택. 실용적 이득 낮음 |

---

## 8. 테스트 매트릭스

### 8.1 `SignedUrlSignerTest` ~12 케이스

| # | 케이스 |
|---|-------|
| S1 | sign → verify 왕복 성공 |
| S2 | 만료 시각 지남 → `SignedUrlExpiredException` |
| S3 | sig 변조 → `SignedUrlInvalidSignatureException` |
| S4 | fileKey 변조 → invalid signature (서명 바인딩 확인) |
| S5 | exp 변조 (verify 호출 시 임의 exp 전달) → invalid signature |
| S6 | sig base64 malformed → invalid signature |
| S7 | 생성자 null secret → NPE |
| S8 | 생성자 secret 길이 < 16 → IllegalArgumentException |
| S9 | 생성자 null clock → NPE |
| S10 | sign null fileKey / expiration → NPE |
| S11 | verify null fileKey / sig → NPE |
| S12 | Clock 주입으로 가짜 현재시각 → 만료 판정 예측 가능 |
| S13 (보너스) | fileKey에 `|` 포함 — 작동은 하되 paranoid 테스트 |

---

## 9. Next

`/pdca do signed-url-signer`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
