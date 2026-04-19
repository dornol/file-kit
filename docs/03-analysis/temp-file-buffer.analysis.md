# Gap Analysis: temp-file-buffer

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: temp-file-buffer
> **Design Ref**: [temp-file-buffer.design.md](../02-design/features/temp-file-buffer.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items checked | 35 |
| Fully matched | 35 |
| Missing / deviated | 0 |
| Build status | ✅ 1197 tests passing, 0 failures |
| Breaking API | 0 |

---

## Gap Details

### §2 유보 결정 5건 (5/5)

| Decision | Design | Impl | Evidence |
|----------|--------|------|----------|
| close() swallow + WARN | 결정 | ✅ | `TempFileBuffer.java:74-76` |
| `Closeable` 채택 | 결정 | ✅ | `TempFileBuffer.java:31` |
| private 생성자 + static factory | 결정 | ✅ | `TempFileBuffer.java:46, 51` |
| suffix `.tmp` 고정 | 결정 | ✅ | `TempFileBuffer.java:34` (SUFFIX 상수) |
| 이름 `TempFileBuffer` | 결정 | ✅ | 클래스명 일치 |

### §3.1 TempFileBuffer API (8/8)

| Item | Evidence |
|------|----------|
| `public final class implements Closeable` | `TempFileBuffer.java:31` |
| `@since 0.1.13` | `TempFileBuffer.java:29` |
| `create(String) throws IOException` + null-check | `TempFileBuffer.java:46-49` (`Objects.requireNonNull`) |
| `path()` close 후에도 동일 Path | final 필드, 리셋 없음 |
| `close()` 멱등 (`closed` 플래그) | `TempFileBuffer.java:68-71` |
| IOException swallow + WARN | `TempFileBuffer.java:74-76` |
| private 생성자 | `TempFileBuffer.java:51-53` |
| Thread-safety JavaDoc 명시 | `TempFileBuffer.java:27` ("Not thread-safe") |

### §4.1 FileUploadService.doUpload (5/5)

| Item | Evidence |
|------|----------|
| tempFile try-with-resources | `FileUploadService.java:273` |
| **Nested** TWR for encryptedFile (dedup-miss 내부) | `:297` (dedup hit fast-return `:283-288` 이후) |
| finally 블록 제거 | 없음 |
| `Path encryptedFile = null` 가드 제거 | 없음 |
| 모든 사용은 `.path()` 경로 | `tempFile.path()` 및 `encryptedFile.path()` |

### §4.2 FileTransferService.doCopy (4/4)

| Item | Evidence |
|------|----------|
| try-with-resources | `FileTransferService.java:232` |
| `TEMP_TRANSFER_PREFIX` package-private | `FileTransferService.java:36` |
| null 가드 + finally + swallow 전부 제거 | 없음 |
| catch 순서 (`FileStorageException` 먼저) 보존 | `:257`, `:259` |

### §5 테스트 매트릭스 (9/9 + bonus 2)

| # | 테스트 | 위치 |
|---|-------|------|
| T1 | `create_returnsPathAndFileExists` | `TempFileBufferTest.java:19` |
| T2 | `close_deletesFile` | `:31` |
| T3 | `close_idempotent` | `:41` |
| T4 | `pathReturnsSameInstance_afterClose` | `:51` |
| T5 | `tryWithResources_normalExit_cleansUp` | `:61` |
| T6 | `tryWithResources_exception_cleansUp` | `:72` |
| T7 | `close_whenFileAlreadyDeleted_isNoop` | `:85` |
| T8 | `create_nullPrefix_throws` | `:95` |
| T9 | `createdFile_hasTmpSuffix` | `:101` |
| Bonus | `create_returnsDistinctPaths` | `:110` |
| Bonus | `closedBuffer_pathStillReadable_butFileGone` | `:120` |

### §8 공개 API (3/3)

- `TempFileBuffer` public final class 추가 ✅
- Breaking 0 (Upload/Transfer 서비스 public 시그니처 불변) ✅
- 내부 리팩토링만 (private 메서드) ✅

### CHANGELOG (2/2)

- Added: TempFileBuffer 기재 (`CHANGELOG.md:15-18`)
- Changed: 리팩토링 + IOException 통일 명시 (`CHANGELOG.md:36-41`)

### Build (1/1)

- ✅ `./gradlew build` 성공, 1197 tests passing (1186 + 11 신규), 0 failures

---

## 관찰 / 개선 제안 (non-blocking)

| # | 항목 | 권고 |
|---|------|------|
| 1 | Test matrix §5.1는 9 케이스만 명시, 실제 11 (bonus 2 추가) | Report에서 `1197 tests (+11 new, including 2 bonus)`로 명시 |
| 2 | `FileUploadService`/`FileTransferService` unused import 없음 (`Files`는 여전히 사용) | Clean |
| 3 | JavaDoc에 `@throws NullPointerException` 명시로 Design보다 약간 풍성 | Keep — 더 나은 문서 |

---

## 결론

Match Rate 100% — iterate 불필요. **`/pdca report temp-file-buffer` 진행 가능**.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1197 tests completed, 0 failures
```
