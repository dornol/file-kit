# Plan: Async Adapter Expansion (Transfer/Delete/Rename)

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | async-adapter-expansion |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `AsyncFileTransferService`, `AsyncFileDeleteService`, `AsyncFileRenameService` — 이전 사이클 패턴의 기계적 확장 |
| Related | 직전 사이클 `async-adapter` follow-up. Transfer/Delete/Rename 남겨둔 것 마무리 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | 직전 사이클에서 Upload/Download async 래퍼만 도입. Transfer(copy/move), Delete, Rename은 여전히 호출부마다 `supplyAsync` 보일러플레이트 필요 |
| **Solution** | 세 서비스 모두 `AsyncXxxService` 래퍼 추가. 전부 checked IOException 없음 → Upload보다 simpler. 이전 사이클의 `AsyncTestSupport` 재사용 |
| **Function/UX Effect** | `async.copyAsync/moveAsync/deleteAsync/renameAsync` 등 1줄 호출. Upload/Download와 대칭 |
| **Core Value** | async 통합이 "5 서비스 전부"로 확장 — 반쪽짜리 지원에서 완전 커버리지로 |

---

## 1. 배경

### 1.1 sync 서비스 public 메서드 (확인됨)

| 서비스 | 메서드 | 반환 | throws |
|--------|-------|------|:---:|
| `FileTransferService` | `copy(key, storageType, bucket)` | `FileMetadata` | — |
| `FileTransferService` | `move(key, storageType, bucket)` | `FileMetadata` | — |
| `FileTransferService` | `copyAll(keys, storageType, bucket)` | `BatchTransferResult` | — |
| `FileTransferService` | `moveAll(keys, storageType, bucket)` | `BatchTransferResult` | — |
| `FileDeleteService` | `delete(key)` | `void` | — |
| `FileDeleteService` | `deleteAll(keys)` | `BatchDeleteResult` | — |
| `FileRenameService` | `rename(key, newName)` | `FileMetadata` | — |

전부 unchecked. IOException wrap 불필요. 래퍼는 그냥 `CompletableFuture.supplyAsync` (+ `runAsync` for delete void).

### 1.2 기존 자산 재사용

- `AsyncTestSupport.unwrap` — 테스트에서 그대로 사용
- `package-info.java` — 패키지 수준 가이드 (executor, exception, cancellation) 이미 정리됨
- Builder 패턴 — 동일 구조 복제

---

## 2. 범위

### 2.1 In Scope

- [ ] `AsyncFileTransferService` + Builder — copyAsync/moveAsync/copyAllAsync/moveAllAsync
- [ ] `AsyncFileDeleteService` + Builder — deleteAsync (`CompletableFuture<Void>`), deleteAllAsync
- [ ] `AsyncFileRenameService` + Builder — renameAsync
- [ ] 세 서비스 각 ~5-7 테스트 케이스
- [ ] CHANGELOG

### 2.2 Out of Scope

- Parallel batch (copyAllAsync 등이 각 항목 병렬 제출) — sync 의미 보존, 별건
- Spring 통합

### 2.3 Design 유보 결정

- **`deleteAsync` 반환 타입**: `CompletableFuture<Void>` (runAsync) vs `CompletableFuture<Object>` — 전자 선택 예상, Design에서 확정
- **세 파일의 클래스 JavaDoc**: package-info 참조 또는 각 파일에 짧은 설명

---

## 3. 요구사항

| ID | 요구사항 |
|----|---------|
| FR-01 | 각 async 메서드는 sync 메서드와 시그니처 대칭 |
| FR-02 | `deleteAsync` → `CompletableFuture<Void>` (runAsync 기반) |
| FR-03 | 예외는 `CompletionException.cause`로 전달 (IOException wrap 없음, 전부 unchecked) |
| FR-04 | Builder에 `executor(Executor)` — 기본 `ForkJoinPool.commonPool()` |
| FR-05 | 세 서비스 모두 `final` class |
| FR-06 | breaking 0 |

---

## 4. 설계 개요

```java
public final class AsyncFileDeleteService {
    public CompletableFuture<Void> deleteAsync(String fileKey) {
        return CompletableFuture.runAsync(() -> sync.delete(fileKey), executor);
    }
    public CompletableFuture<BatchDeleteResult> deleteAllAsync(Collection<String> fileKeys) {
        return CompletableFuture.supplyAsync(() -> sync.deleteAll(fileKeys), executor);
    }
    // Builder, etc.
}
```

Transfer/Rename도 동일 shape.

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `AsyncFileTransferService` + 테스트 | 50분 |
| 2 | `AsyncFileDeleteService` + 테스트 | 30분 |
| 3 | `AsyncFileRenameService` + 테스트 | 20분 |
| 4 | CHANGELOG + 회귀 | 15분 |

총: **약 2시간** (기계적 확장이라 짧음)

---

## 6. 공개 API

### 추가
- `async.AsyncFileTransferService` + Builder
- `async.AsyncFileDeleteService` + Builder
- `async.AsyncFileRenameService` + Builder

### Breaking
- 없음

---

## 7. Next

1. [ ] `/pdca design async-adapter-expansion`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
