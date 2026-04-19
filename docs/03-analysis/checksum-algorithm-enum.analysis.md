# Gap Analysis: checksum-algorithm-enum

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: checksum-algorithm-enum
> **Design Ref**: [checksum-algorithm-enum.design.md](../02-design/features/checksum-algorithm-enum.design.md) (+Plan §6-§8)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 10 checked · 10 fully matched |
| Build | ✅ 1295 tests passing (+13), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | `ChecksumAlgorithm` enum 5 values | `ChecksumAlgorithm.java:20-24` MD5/SHA_1/SHA_256/SHA_384/SHA_512 | ✅ |
| 2 | `standardName()` accessor | L33-35 | ✅ |
| 3 | `MessageDigestChecksumCalculator(ChecksumAlgorithm)` + null-check | `MessageDigestChecksumCalculator.java:36-38` (`Objects.requireNonNull`) | ✅ |
| 4 | `checksum(byte[])`/`checksum(InputStream)`/`newComputation()` 구현 | L47, 51-65, 72 | ✅ |
| 5 | `NoSuchAlgorithmException` → `IllegalStateException` wrap | L79-81 | ✅ |
| 6 | `HEX` 상수 재사용 (static final) | L27 | ✅ |
| 7 | `MessageDigestComputation` inner 재구현 (double-finish guard) | L83-106 | ✅ |
| 8 | `Sha256ChecksumCalculator` 서브클래스 (0-arg ctor) | `Sha256ChecksumCalculator.java:11-15`, 16줄로 압축 | ✅ |
| 9 | `@since 0.1.20` 명시 | 두 신규 파일 | ✅ |
| 10 | CHANGELOG 엔트리 | Added 섹션에 enum + 제네릭 calc + Sha256 유지 명시 | ✅ |

### 추가 테스트 (Design §8 커버)

- `ChecksumAlgorithmTest`: A1~A3 + explicit names = 4 tests
- `MessageDigestChecksumCalculatorTest`: M1~M8 + sanity subclass parity = 9 tests
- 기존 `Sha256ChecksumCalculatorTest` 회귀 0

---

## 결론

Match Rate 100% — `/pdca report` 진행.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1295 tests completed, 0 failures
```
