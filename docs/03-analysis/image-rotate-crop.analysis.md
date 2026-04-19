# Gap Analysis: image-rotate-crop

> **Phase**: Check (PDCA) · 2026-04-19

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 12 checked · 12 fully matched |
| Build | ✅ 1354 tests passing (+21), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | `RotateAngle` enum (90/180/270) + `@since 0.1.24` | `RotateAngle.java:9,14` | ✅ |
| 2 | `RotateOption` record, null angle NPE, quality 범위 검증, factory | `RotateOption.java:19,25,36` | ✅ |
| 3 | `RotateResult` record | `RotateResult.java` | ✅ |
| 4 | `ImageRotator` SPI | `ImageRotator.java:10` | ✅ |
| 5 | `ImageIORotator` — 90/270 swap, 180 유지 | `ImageIORotator.java:60-65` | ✅ |
| 6 | `ImageIOUtils.readImage/writeImage/resolveOutputFormat` 재사용 | `ImageIORotator.java:37-41` | ✅ |
| 7 | `CropOption` record, (x,y) ≥ 0, (w,h) > 0 검증 + factory | `CropOption.java:32-48,55` | ✅ |
| 8 | `CropResult` record | `CropResult.java` | ✅ |
| 9 | `ImageCropper` SPI | `ImageCropper.java:10` | ✅ |
| 10 | `ImageIOCropper` — `getSubimage` + 경계 검증 | `ImageIOCropper.java:35-62` | ✅ |
| 11 | 경계 초과 → `IllegalArgumentException` 명시적 전파 (cropper L51, 외부 option 생성자 L30) | — | ✅ |
| 12 | 테스트 R1-R10 + C1-C11 | `ImageIORotatorTest`, `ImageIOCropperTest` | ✅ |

---

## 결론

Match Rate 100% — simplify + report 진행.

## Build

```
./gradlew build
BUILD SUCCESSFUL
1354 tests passing, 0 failures
```
