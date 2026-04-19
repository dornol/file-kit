# Gap Analysis: async-parallel-batch

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: async-parallel-batch
> **Design Ref**: Plan §7-§9 (Design 통합)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **100%** |
| Design items | 10 checked · 10 fully matched |
| Build | ✅ 1304 tests passing (+9), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | `copyAllParallelAsync` 추가 | `AsyncFileTransferService.java` L~85 | ✅ |
| 2 | `moveAllParallelAsync` 추가 | 동일 파일 | ✅ |
| 3 | `deleteAllParallelAsync` 추가 | `AsyncFileDeleteService.java` | ✅ |
| 4 | `AsyncInternal.unwrapMessage` package-private 추출 | `AsyncInternal.java` 신규 | ✅ |
| 5 | Upload 제외 (Plan §1.2 dedup TOCTOU 근거) | `AsyncFileUploadService` 미변경 | ✅ |
| 6 | 각 항목 executor에 **개별 제출** (FR-01) | `allInParallel` 내부 `op.apply(key)` 각 호출이 `copyAsync` (supplyAsync 기반) → 개별 태스크 | ✅ |
| 7 | `allOf` 패턴으로 BatchXxxResult 생성 (FR-02) | `CompletableFuture.allOf(...).thenApply(v -> ...)` | ✅ |
| 8 | 개별 실패가 future를 실패시키지 않음 (FR-03) | `handle((result, ex) -> ...)` | ✅ |
| 9 | 실패 메시지 `CompletionException` unwrap (FR-04) | `AsyncInternal.unwrapMessage` | ✅ |
| 10 | CHANGELOG 엔트리 + dedup 제외 근거 명시 | CHANGELOG Added 섹션 | ✅ |

### 테스트 매트릭스 커버

- Transfer P1~P5 (성공, 혼합, 빈, 메시지 unwrap, move parallel) ✅
- Delete P1~P4 (성공, 혼합, 빈, 메시지 unwrap) ✅

---

## 결론

Match Rate 100% — `/pdca report` 진행.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1304 tests completed, 0 failures
```
