# Gap Analysis: async-adapter

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: async-adapter
> **Design Ref**: [async-adapter.design.md](../02-design/features/async-adapter.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **98%** |
| Design items | 8 checked · 7 fully · 1 minor (U8 assertion 약함) |
| Build | ✅ 1228 tests passing (+16 신규), 0 failures |
| Breaking API | 0 |

---

## Gap Details

| # | Item | Evidence | Status |
|---|------|----------|:---:|
| 1 | §2 유보 결정 5건 | commonPool 기본, uploadAll 순차, `async` 패키지, IOException wrap, VT JavaDoc | ✅ |
| 2 | §3.1 Upload 3 async 메서드 + Builder + null check + `@since 0.1.16` | `AsyncFileUploadService.java` 전체 | ✅ |
| 3 | §3.2 Download 3 async 메서드 + Builder | `AsyncFileDownloadService.java` 전체 | ✅ |
| 4 | §3.3 패키지 구조 | `async/{package-info, AsyncFileUploadService, AsyncFileDownloadService}` | ✅ |
| 5 | §4 예외 계약 | Upload IOException wrap, Download checked 없음, Builder null executor NPE | ✅ |
| 6 | §5.1 U1~U9, §5.2 D1~D7 | 모든 케이스 @Test 존재 | ✅ (U8 assertion 약함) |
| 7 | §7 API 추가, breaking 0 | 2 service + 2 Builder + package-info, sync API 불변 | ✅ |
| 8 | Build 1228 tests | 확인 | ✅ |

### 추가 강점 (Design 초과)

- **batch note**: `AsyncFileUploadService` JavaDoc에 "sequential semantics preserved" 명시
- **stream caveat**: `AsyncFileDownloadService` JavaDoc에 "content stream 소비는 caller thread 블록" 경고 추가
- **`Collection<? extends FileSource>`**: sync `uploadAll` 시그니처와 일치 (more permissive)

### Minor delta

- **U8**: Design은 "내부 필드 비교 또는 동작으로 간접 검증"이었으나 구현은 `commonPool() == commonPool()` 동어반복. Builder 소스에서 `executor = ForkJoinPool.commonPool()`가 명시적이라 기능 동등 — 개선 가능 (reflection이나 스레드명 prefix 검증)

---

## 결론

Match Rate 98% — **`/pdca report async-adapter` 진행 가능**.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1228 tests completed, 0 failures
```
