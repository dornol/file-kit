# Gap Analysis: configurable-temp-directory

> **Phase**: Check (PDCA) · 2026-04-19

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 10 checked · 10 fully matched |
| Build | ✅ 1358 tests passing (+4), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | `TempFileBuffer.create(Path, String)` 오버로드 | `TempFileBuffer.java:51-63` | ✅ |
| 2 | `TempFileBuffer.create(String)` → `create(null, prefix)` 위임 | L44-46 | ✅ |
| 3 | `FileUploadService.Builder.tempDirectory(Path)` | `FileUploadService.java` Builder | ✅ |
| 4 | `FileTransferService.Builder.tempDirectory(Path)` | `FileTransferService.java` Builder | ✅ |
| 5 | `FileDownloadService.Builder.tempDirectory(Path)` | `FileDownloadService.java` Builder | ✅ |
| 6 | `DecryptionHelper.decryptToStream(InputStream, FileEncryptor, Path)` 오버로드 | `DecryptionHelper.java` | ✅ |
| 7 | 기본 null → 시스템 tmpdir (backward compat) | `Files.createTempFile(prefix, SUFFIX)` 분기 | ✅ |
| 8 | `FileDownloadService.decryptToStream` → 3-arg helper 호출로 교체 | `FileDownloadService.java:215` | ✅ |
| 9 | `ingestIoException_propagates_andCleansTempFile` `@TempDir` 재작성 | `FileUploadServiceTest.java:496-516` | ✅ |
| 10 | TD1-TD4 테스트 추가 | `TempFileBufferTest.java` | ✅ |

### 부수 정리
- `countUploadTempFiles` helper 완전 제거 — 시스템 tmpdir 스캔 로직 사라짐
- `DirectoryStream` import 제거, `Stream` import 추가
- `@since 0.1.25` 명시 (TempFileBuffer 오버로드, 3 Builder 메서드, DecryptionHelper 3-arg)

---

## 결론

Match Rate 100% — simplify + report 진행.

## Build

```
./gradlew build
BUILD SUCCESSFUL
1358 tests passing, 0 failures
```
