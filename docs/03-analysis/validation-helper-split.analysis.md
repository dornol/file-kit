# Gap Analysis: validation-helper-split

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: validation-helper-split
> **Design Ref**: [validation-helper-split.design.md](../02-design/features/validation-helper-split.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 10 checked · 10 fully matched |
| Build | ✅ 1270 tests passing (+23 신규), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | §2 유보 결정 (`public final`, 기존 생성자, 중복 테스트 허용) | `MediaTypeValidator.java:23`, `ImageDimensionValidator.java:26` 모두 `public final`; `FileValidationHelper(MediaTypeDetector)` 생성자 유지 | ✅ |
| 2 | §2.1 MediaTypeValidator (`validate`, `validateAll`, `@since 0.1.18`, null-check) | L44/92/22, `Objects.requireNonNull` L34/45/46/93 | ✅ |
| 3 | §2.2 ImageDimensionValidator (`validate`, `validateAll`, `@since 0.1.18`) | L41/93/24 | ✅ |
| 4 | §2.3 FileValidationHelper 재구조 | 168L, size/empty/filename inline, media/dim 위임. 공개 API 불변 | ✅ |
| 5 | §2.4 동작 동등성 (로그, side effect, NPE 타이밍) | 로그 메시지 verbatim 유지, NPE는 `Objects.requireNonNull` 타이밍 동일 | ✅ |
| 6 | §3.1 M1~M10 + M11-M12 bonus | `MediaTypeValidatorTest.java` 12 @Test | ✅ |
| 7 | §3.2 I1~I8 + I9-I11 bonus | `ImageDimensionValidatorTest.java` 11 @Test | ✅ |
| 8 | 기존 `FileValidationHelperTest` 회귀 0 | 테스트 파일 그대로, facade 경로로 통과 | ✅ |
| 9 | Build 1270 tests (1247+23) | `./gradlew build` 성공 | ✅ |
| 10 | CHANGELOG 엔트리 | Added 섹션에 두 validator + facade 보존 언급 | ✅ |

---

## 비고

- `FileValidationHelper`는 logger 유지 (`isFileSizeExceeded`/`isValidFilename` 내부에서 사용). 정당
- `getExtension` private static은 `MediaTypeValidator`로 동일 이동 (Design §2.1 "unchanged" 명시 그대로)
- 23 신규 테스트: 12 MediaType (M1~M12) + 11 ImageDimension (I1~I11)

---

## 결론

Match Rate 100% — `/pdca report validation-helper-split` 진행 가능.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1270 tests completed, 0 failures
```
