# Gap Analysis: upload-pipeline-io

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: upload-pipeline-io
> **Design Ref**: [upload-pipeline-io.design.md](../02-design/features/upload-pipeline-io.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** (follow-up 적용 후) |
| Design items checked | 34 |
| Fully matched | 33 |
| Scope-trimmed | 1 (U6 — optional per Design) |
| Build status | ✅ 1188 tests passing (follow-up 2건 +2 테스트) |
| Breaking API | 0 (soft: 16 KiB format buffer limit, documented) |

> **History**: 1차 분석 시 Match Rate 97% (3건 간접 매치). 후속 권고 1·2 적용 후 직접 매치로 전환.

---

## Gap Details

| # | Design Item | Reference | Status | Evidence |
|---|-------------|-----------|:------:|----------|
| 1a | Magic byte default 16 KiB, min 1 KiB, Builder configurable | §2 | ✅ | `MagicByteBuffer.java:38,41`; `FileUploadService.java:115,169-177` |
| 1b | No fallback on format extraction failure | §2 | ✅ | `FileUploadService.java:289` — single `extract()` call on header.asInputStream() |
| 1c | Virus scan always runs; dedup AFTER virus scan | §2 | ✅ | `FileUploadService.java:280` (scan) precedes `:282` (findByChecksum) |
| 1d | `MagicByteBuffer` is `public final` in `io/` | §2 | ✅ | `MagicByteBuffer.java:1,35` |
| 2 | `DEFAULT_SIZE=16384`, `MIN_SIZE=1024` 상수 | §3.1 | ✅ | `MagicByteBuffer.java:38,41` |
| 3 | 무인자 생성자 → DEFAULT_SIZE | §3.1 | ✅ | `MagicByteBuffer.java:47-49` |
| 4 | `MagicByteBuffer(int)` capacity<MIN_SIZE 시 IAE | §3.1 | ✅ | `MagicByteBuffer.java:55-61` |
| 5 | `observe(byte[],int,int)` capacity까지 복사, 초과분 무시 | §3.1 | ✅ | `MagicByteBuffer.java:68-76` (bonus: `len<=0` 가드) |
| 6 | `size()`, `capacity()`, `asInputStream()` | §3.1 | ✅ | `MagicByteBuffer.java:79,84,96` |
| 7 | Not thread-safe 문서 | §3.1 | ✅ | `MagicByteBuffer.java:30-31` |
| 8 | `@since 0.1.12` | §3.1 | ✅ | `MagicByteBuffer.java:33` |
| 9 | `Builder.formatHeaderBufferSize(int)` 추가 | §4.2 | ✅ | `FileUploadService.java:169-177` |
| 10 | Default = `MagicByteBuffer.DEFAULT_SIZE` | §4.2 | ✅ | `FileUploadService.java:115` |
| 11 | bytes < MIN_SIZE 시 IAE | §4.2 | ✅ | `FileUploadService.java:170-174` |
| 12 | Pass 1: source → tempFile + checksum.update + header.observe 단일 read | §4.3 | ✅ | `FileUploadService.java:337-353` (`teeIngest`) |
| 13 | After pass 1: `checksum = computation.finish()` | §4.3 | ✅ | `FileUploadService.java:276` |
| 14 | Virus scan이 dedup 전 | §4.3 | ✅ | `FileUploadService.java:280` |
| 15 | Dedup hit → fast return | §4.3 | ✅ | `FileUploadService.java:282-287` |
| 16 | Dedup miss → `extract(header.asInputStream())` | §4.3 | ✅ | `FileUploadService.java:289` |
| 17 | tempFile re-read 제거 (checksum/format 경로) | §4.3 | ✅ | `Files.newInputStream(tempFile)`는 scan/encrypt 2회만 |
| 18 | M1-M8 + bonus | §6.1 | ✅ | `MagicByteBufferTest.java:15-111` (10 케이스) |
| 19 | Builder `formatHeaderBufferSize` 검증 (U9) | §6.2 | ✅ | `FileUploadServiceTest.java:128-160` (5 케이스) |
| 20 | 기존 테스트 migrate: `newComputation()` 스텁 + `byte[]` matcher | §6.3 | ✅ | `FileUploadServiceTest.java:64-70` + 12곳 교체 |
| 21 | `MagicByteBuffer` public class | §9 | ✅ | |
| 22 | `Builder.formatHeaderBufferSize` new | §9 | ✅ | |
| 23 | SPI 변경 0 | §9 | ✅ | |
| 24 | Soft-breaking (16 KiB) 문서화 | §9 | ✅ | CHANGELOG:24-27 |
| 25-27 | CHANGELOG Added/Changed/Migration notes | task | ✅ | CHANGELOG:12-31 |
| 28 | Build 1186 tests, 0 failures | task | ✅ | 확인 |
| 29 | U1 dedup hit → virus scan 실행 | §6.2 | ✅ | `clean_withDuplicate_returnsDuplicateWithoutStorage` (L611) |
| 30 | U2/U3/U4 dedup hit → format/encrypt/storage 미호출 | §6.2 | ✅ | `duplicateChecksum_returnsExistingWithoutUpload` (L412) + **신규** `duplicateChecksum_doesNotInvokeEncryptor` (`verify(encryptor, never()).encrypt(...)` 직접 매치) |
| 31 | U5 dedup miss → full flow | §6.2 | ✅ | `fullFlow_uploadAndReturnMetadata` |
| 32 | U6 tempFile read count 통합 테스트 | §6.2 | ⏭ scope trim | Plan §5.1 / Design §6.2에서 "선택" 명시. 코드 검사로 2 reads 확정 |
| 33 | U7 ingest IOException → tempFile delete | §6.2 | ✅ | **신규** `ingestIoException_propagates_andCleansTempFile` — throwing InputStream + 시스템 temp 디렉토리 orphan 카운트 검증 |
| 34 | U8 format extractor magic-byte 정확도 | §6.2 | ✅ | 기존 통합 테스트 경로 |

---

## 의도적 Scope Trim

- **U6 (read-count 통합 테스트)**: Plan §5.1 / Design §6.2에서 명시적으로 "선택". 코드 grep으로 `Files.newInputStream(tempFile)` 호출 지점이 scan + encrypt 2곳임을 직접 확인 가능해 테스트 필요성 낮음.
- **U7 / U8 전용 단위 테스트**: 미추가. 기존 통합 테스트(`UploadDownloadIntegrationTest`, `EncryptionIntegrationTest`, `BatchUploadIntegrationTest`)가 회귀 커버리지 제공. 1186 tests 통과로 검증.

---

## Recommendations (적용 완료)

| # | 항목 | 상태 |
|---|------|:---:|
| 1 | 전용 테스트로 `fileEncryptor.encrypt` 미호출 직접 검증 | ✅ Applied |
| 2 | U7 전용 테스트 (ingest IOException + tempFile cleanup) | ✅ Applied |

---

## 결론

Match Rate 97% — 90% 임계 초과. Iterate 불필요. **`/pdca report upload-pipeline-io` 진행 가능**.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1188 tests completed, 0 failures   (초기 1186 + follow-up 2건)
```
