# Plan: Async Parallel Batch Operations

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | async-parallel-batch |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `AsyncFileTransferService`와 `AsyncFileDeleteService`에 parallel 배치 메서드 추가. Upload는 dedup 안전성 이유로 제외 |
| Related | 직전 async-adapter 사이클의 §2.2 "parallel batch out of scope" 항목 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | async adapter의 `copyAllAsync`/`moveAllAsync`/`deleteAllAsync`가 sync batch를 단일 executor 태스크로 래핑 — 순차 실행. 100건 복사 시 100 × RTT. 병렬 실행으로 큰 속도 향상 가능하지만 사용자가 직접 `allOf` 조립해야 함 |
| **Solution** | `copyAllParallelAsync`, `moveAllParallelAsync`, `deleteAllParallelAsync` 추가 — 각 항목을 executor에 개별 제출 → `allOf` 조합 → `BatchXxxResult` 생성. Upload는 dedup TOCTOU 위험으로 **의도적 제외** |
| **Function/UX Effect** | 100건 transfer/delete 처리 시간이 N × RTT → max(RTT) 수준으로 단축 (executor 처리 가능 수준 내) |
| **Core Value** | 기존 sequential async는 유지 (안전 기본). parallel은 명시적 "Parallel" 접미사로 선택 — 개발자가 trade-off 인지하고 선택 |

---

## 1. 배경

### 1.1 현재 배치 async 한계

각 async adapter의 `xxxAllAsync`는:
```java
public CompletableFuture<BatchTransferResult> copyAllAsync(
        Collection<String> fileKeys, Enum<?> type, String bucket) {
    return CompletableFuture.supplyAsync(
            () -> sync.copyAll(fileKeys, type, bucket),  // ← 순차
            executor);
}
```

한 executor 태스크가 순차 실행. 개별 copy I/O 소요 시간이 합산됨.

### 1.2 Upload를 제외하는 이유

Sync `FileUploadService.uploadAll` 역시 순차인데, 특히 **dedup 체크가 TOCTOU 취약**:

```
T1: findByChecksum("abc") → null (not found)
T2: findByChecksum("abc") → null (not found)  ← 동시 진행
T1: save(metadata with checksum="abc")
T2: save(metadata with checksum="abc") ← unique 제약 위반 or 중복 저장
```

JavaDoc(현 L46-53)도 "add a unique constraint on the checksum column" 권장. 병렬화는 이 문제를 증폭시킴. 별도 설계(per-checksum lock, 순차 dedup + 병렬 upload 분리) 필요 → 별건.

### 1.3 Transfer/Delete는 안전

- Transfer: 각 copy/move는 새 UUID 할당 → 충돌 없음
- Delete: 대상 파일이 서로 다름 → 충돌 없음

---

## 2. 범위

### 2.1 In Scope

- [ ] `AsyncFileTransferService.copyAllParallelAsync(keys, type, bucket)` → `CompletableFuture<BatchTransferResult>`
- [ ] `AsyncFileTransferService.moveAllParallelAsync(...)` → 동일
- [ ] `AsyncFileDeleteService.deleteAllParallelAsync(keys)` → `CompletableFuture<BatchDeleteResult>`
- [ ] 각 method는 개별 sync 호출을 executor에 병렬 제출, `allOf`로 조합, `BatchXxxResult` 생성
- [ ] 실패한 항목은 `failed` map에 기록 (기존 sync batch semantics 유지)
- [ ] 각 신규 메서드에 테스트 ~4-5 케이스
- [ ] CHANGELOG

### 2.2 Out of Scope

- `AsyncFileUploadService.uploadAllParallelAsync` — dedup 안전성 이유로 **의도적 제외**. 별건 피처
- Sync 서비스의 parallel batch (async 범위 밖)
- `parallelism` 파라미터 (executor에 맡김 — 외부 주입된 executor의 특성)
- Cancellation — 개별 future cancel은 executor 특성, allOf 단독으로 stop 불가

### 2.3 유보 결정

- **메서드 이름**: `copyAllParallelAsync` vs `copyAllConcurrentAsync` vs 오버로드 `copyAllAsync(boolean parallel)`
- **실패 메시지 형식**: 원 예외 `getMessage()` vs 클래스명 + message (sync와 맞춰야)
- **순서 보존**: 결과 `succeeded` list가 입력 순서와 같은지 — 병렬이라 원래 순서 보존 불가. 문서 명시

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | 각 항목이 executor에 **개별** submit — 순차 실행이 아님 | High |
| FR-02 | 모든 항목 완료 후 `BatchXxxResult` 반환 (allOf 패턴) | High |
| FR-03 | 개별 실패가 전체 future를 실패시키지 않음 — `failed` map에 기록 | High |
| FR-04 | 실패 메시지는 sync `uploadAll/copyAll` 포맷과 동일 (원 예외 `getMessage`) | High |
| FR-05 | 빈 컬렉션 입력 → 즉시 완료 (empty result) | Medium |
| FR-06 | 순서 보존 아님 — 문서에 명시 | Medium |

---

## 4. 구현 설계

### 4.1 공통 패턴 (Transfer/Delete 공통)

```java
public CompletableFuture<BatchXxxResult> xxxAllParallelAsync(...) {
    List<CompletableFuture<Entry>> futures = keys.stream()
            .map(key -> xxxAsync(key, ...)
                    .<Entry>handle((result, ex) -> ex == null
                            ? Entry.succeeded(result)
                            : Entry.failed(key, unwrapMessage(ex))))
            .toList();

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> {
                List<Success> succeeded = new ArrayList<>();
                Map<String, String> failed = new LinkedHashMap<>();
                for (CompletableFuture<Entry> f : futures) {
                    Entry e = f.join();
                    if (e.isSuccess()) succeeded.add(e.success());
                    else failed.put(e.key(), e.reason());
                }
                return new BatchXxxResult(succeeded, failed);
            });
}
```

### 4.2 `unwrapMessage` helper

CompletableFuture는 발생 예외를 `CompletionException`으로 감싸므로 원 메시지 추출 필요:

```java
private static String unwrapMessage(Throwable t) {
    Throwable cause = t instanceof CompletionException && t.getCause() != null
            ? t.getCause() : t;
    return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
}
```

추후 재사용 위해 `AsyncTestSupport` 근처에 `AsyncInternal` 유틸 두기 (package-private).

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `AsyncFileTransferService.copyAllParallelAsync/moveAllParallelAsync` | 40분 |
| 2 | `AsyncFileDeleteService.deleteAllParallelAsync` | 20분 |
| 3 | 각 서비스 테스트 ~4-5 케이스 추가 | 60분 |
| 4 | CHANGELOG | 10분 |
| 5 | 회귀 | 10분 |

총: **약 2.5시간**

---

## 6. 공개 API

### 추가
- `AsyncFileTransferService.copyAllParallelAsync(Collection<String>, Enum<?>, String)`
- `AsyncFileTransferService.moveAllParallelAsync(Collection<String>, Enum<?>, String)`
- `AsyncFileDeleteService.deleteAllParallelAsync(Collection<String>)`

### Breaking
- 없음

---

# Design

## 7. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 메서드 이름 | **`xxxAllParallelAsync`** | `xxxAllAsync`와 명확히 구분. boolean 플래그는 발견성 ↓ |
| 실패 메시지 | `cause.getMessage()` (null 시 `getSimpleName()`) | `CompletionException` unwrap 후 원 메시지. sync batch와 동일 포맷 유지 |
| 순서 보존 | **보존 안 함** | 병렬 특성상 불가. Javadoc에 명시 |
| unwrap 로직 위치 | package-private `AsyncInternal` (async/) | 3곳 사용 → 공유 |

---

## 8. API 상세

### 8.1 `AsyncFileTransferService.copyAllParallelAsync`

```java
/**
 * Asynchronously copies multiple files in parallel. Each source key is
 * submitted as an independent task on the configured executor.
 *
 * <p><b>Ordering:</b> the returned {@link BatchTransferResult#succeeded()}
 * list is NOT guaranteed to match input order.</p>
 *
 * <p>Individual copy failures do not fail the returned future — they land in
 * {@link BatchTransferResult#failed()} keyed by the input source key.</p>
 *
 * @since 0.1.21
 */
public CompletableFuture<BatchTransferResult> copyAllParallelAsync(
        Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) { ... }
```

### 8.2 `AsyncInternal` (package-private utility)

```java
final class AsyncInternal {
    private AsyncInternal() {}

    static String unwrapMessage(Throwable t) {
        Throwable cause = (t instanceof CompletionException && t.getCause() != null)
                ? t.getCause() : t;
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }
}
```

---

## 9. 테스트 매트릭스

### 9.1 Transfer (copy + move, 각 4-5)

| # | 케이스 |
|---|-------|
| T1 | 성공 3건 → BatchTransferResult.succeeded 3, failed empty |
| T2 | 2 성공 + 1 실패 → succeeded 2, failed 1 (해당 key → 메시지) |
| T3 | 빈 입력 → empty result (allSucceeded true) |
| T4 | 실패 메시지 unwrap 확인 (원 예외 메시지) |
| T5 | 주입된 executor로 실제 병렬 실행 확인 (Thread 이름 2+ 종류) |

### 9.2 Delete (4)

| # | 케이스 |
|---|-------|
| D1 | 성공 3건 |
| D2 | 혼합 |
| D3 | 빈 입력 |
| D4 | 예외 unwrap |

## 10. Next

`/pdca do async-parallel-batch`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
