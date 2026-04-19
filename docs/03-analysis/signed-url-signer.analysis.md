# Gap Analysis: signed-url-signer

> **Phase**: Check (PDCA) · 2026-04-19

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 10 checked · 10 fully matched |
| Build | ✅ 1333 tests passing (+14), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | 새 패키지 `url/` | `url/SignedUrlSigner.java` 등 4개 파일 | ✅ |
| 2 | `SignedUrlSigner` public final, HMAC-SHA256, `@since 0.1.23` | `SignedUrlSigner.java:59,62` | ✅ |
| 3 | `sign(fileKey, Instant)` → `"exp=...&sig=..."` | L107-113 | ✅ |
| 4 | `verify(fileKey, long exp, String sig)` | L121-138 | ✅ |
| 5 | 만료 → `SignedUrlExpiredException`, 위조/malformed → `SignedUrlInvalidSignatureException` | L125-128, L130-137 | ✅ |
| 6 | Constant-time 비교 `MessageDigest.isEqual` | L136 | ✅ |
| 7 | 생성자 secret < 16 bytes → IllegalArgumentException | L85-88 | ✅ |
| 8 | `Clock` 주입 가능 (testability) | L92-102 | ✅ |
| 9 | URL-safe Base64 no padding (encoder) + tolerant decoder | L51-52 | ✅ |
| 10 | 테스트 S1-S13 + S8b | 14 @Test | ✅ |

### CHANGELOG

- Added 섹션에 signer + 3 예외 설명 ✅
- 인가 경계 명시 ("signer does not authorize users") ✅

---

## 결론

Match Rate 100% — simplify + report 진행.

## Build

```
./gradlew build
BUILD SUCCESSFUL
1333 tests passing, 0 failures
```
