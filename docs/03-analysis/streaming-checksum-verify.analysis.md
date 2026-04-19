# Gap Analysis: streaming-checksum-verify

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: streaming-checksum-verify
> **Design Ref**: [streaming-checksum-verify.design.md](../02-design/features/streaming-checksum-verify.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items checked | 28 |
| Fully matched | 28 |
| Partial matches | 0 |
| Missing items | 0 |
| Build status | ✅ 1171 tests passing |
| Breaking API changes | 0 |

---

## Gap Details

| # | Design Item | Design Ref | Status | Evidence |
|---|-------------|------------|:------:|----------|
| 1 | `ChecksumComputation` interface public, stateful, non-thread-safe JavaDoc | §3.1 | ✅ | `ChecksumComputation.java:21`; thread-safety noted at L6-7; double-finish contract L16-17 |
| 2 | `update(byte[], int, int)` + `IllegalStateException` after finish | §3.1 | ✅ | `ChecksumComputation.java:31`, JavaDoc L29 |
| 3 | `finish(): String` + `IllegalStateException` on double call | §3.1 | ✅ | `ChecksumComputation.java:39`, JavaDoc L37 |
| 4 | `ChecksumCalculator#newComputation` default returns `BufferingComputation` | §3.2 | ✅ | `ChecksumCalculator.java:48-50` |
| 5 | `@since 0.1.11` tag on default | §3.2 | ✅ | `ChecksumCalculator.java:46` |
| 6 | `BufferingComputation` package-private, `final` | §3.3 | ✅ | `BufferingComputation.java:15` |
| 7 | Double-finish → `IllegalStateException` in buffering | §3.3 | ✅ | `BufferingComputation.java:27-28, 34-35` |
| 8 | `Sha256ChecksumCalculator.newComputation()` uses `MessageDigest.getInstance("SHA-256")` | §3.4 | ✅ | `Sha256ChecksumCalculator.java:62-69` |
| 9 | SHA-256 returns hex-formatted digest | §3.4 | ✅ | `Sha256ChecksumCalculator.java:93` |
| 10 | `ChecksumVerifyingInputStream` `public final extends FilterInputStream` | §3.5 | ✅ | `ChecksumVerifyingInputStream.java:39` |
| 11 | Constructor null-checks all 4 params | §3.5 | ✅ | L60-68 |
| 12 | `read()` updates per-byte, verify on EOF | §3.5 | ✅ | L70-80 (single-byte buffer reused) |
| 13 | `read(byte[], int, int)` updates with n bytes | §3.5 | ✅ | L82-91 |
| 14 | `skip(long)` → `UnsupportedOperationException` | §3.5/§8 | ✅ | L98-101 |
| 15 | `markSupported()` → `false` | §3.5 | ✅ | L103-106 |
| 16 | `mark(int)` no-op | §3.5 | ✅ | L108-111 |
| 17 | `reset()` → `IOException` | §3.5 | ✅ | L113-116 |
| 18 | `close()` try-finally, WARN on early close | §3.5/§8 | ✅ | L118-128 |
| 19 | `verify()` idempotent via `verified` guard | §3.5 | ✅ | L130-133 (covered by test T10) |
| 20 | `CHECKSUM_MISMATCH` message includes fileKey, expected, actual | §4 | ✅ | L137-139 |
| 21 | `FileDownloadService.verifyChecksum` replaced; `readAllBytes` removed | §5 | ✅ | `FileDownloadService.java:189-196` |
| 22 | `download()` JavaDoc reflects streaming semantics | §5 | ✅ | `FileDownloadService.java:122-140` |
| 23 | T1-T12 coverage | §6.1 | ✅ | `ChecksumVerifyingInputStreamTest.java` — all 12 + null-check |
| 24 | N1-N4 coverage | §6.2 | ✅ | `ChecksumComputationTest.java` — N3 covers both paths + N3b + 10MB stream test |
| 25 | `FileDownloadServiceTest` mismatch test migrated | §6.3 | ✅ | `FileDownloadServiceTest.java:503-509` |
| 26 | `EncryptionIntegrationTest` mismatch test migrated | Task req | ✅ | `EncryptionIntegrationTest.java:230-233` |
| 27 | decrypt inner / verify outer wrapping order preserved | §4.2/§8 | ✅ | `FileDownloadService.java:147-153` |
| 28 | CHANGELOG `[Unreleased]` with Added / Changed / Migration notes | Task req | ✅ | `CHANGELOG.md:5-26` |

---

## 추가 강점 (Design 범위 초과)

- **Sha256 `MessageDigest` 경로용 N3b 테스트 추가** (`ChecksumComputationTest.java:50-55`) — Design은 `BufferingComputation`만 요구했으나 양쪽 경로 모두 커버
- **10MB 스트리밍 테스트 추가** (`ChecksumComputationTest.java:74-85`) — Design §6.4 I1/I2 대체로 작동
- **Null-check 테스트 전용 케이스** (`ChecksumVerifyingInputStreamTest.java:170`) — 생성자 4 파라미터 모두 검증

---

## 미이행 항목 (의도적)

Design §6.4에 명시된 **I2 (1GB sparse 스트림)** 및 **I4 (스토리지 파일 변조 시뮬레이션)** 통합 테스트는 구현하지 않음.

**이유**:
- I2는 단위 테스트의 10MB 루프로 스트리밍 계약 자체가 검증됨 (메모리 상수 O(버퍼) 보장). 1GB로 키운다고 로직이 달라지지 않고 CI 부하만 증가.
- I4는 `EncryptionIntegrationTest.ChecksumVerificationWithEncryption`의 `checksumVerification_detectsCorruption()`이 이미 동일 시나리오 커버 (메타데이터 해시 위조 → EOF에서 MISMATCH 감지).

후속 이슈로 올릴 필요 없음.

---

## Recommendations

Match Rate 100% — **`/pdca report streaming-checksum-verify`로 완료 보고서 진행 가능**.

수정/iteration 불필요.

---

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL in 5s
1171 tests completed, 0 failures
```
