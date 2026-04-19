# Design: Async Adapter Expansion

> **Plan**: [async-adapter-expansion.plan.md](../../01-plan/features/async-adapter-expansion.plan.md)
> **Status**: Draft · 2026-04-19

---

## 1. 유보 결정 확정

| 쟁점 | 결정 |
|------|------|
| `deleteAsync` 반환 타입 | **`CompletableFuture<Void>`** via `runAsync` |
| 클래스 JavaDoc | **package-info 참조** + 각 파일 1-2줄 서비스 설명 |

---

## 2. API 정의

### 2.1 `AsyncFileTransferService`

```java
package io.github.dornol.filekit.async;

public final class AsyncFileTransferService {
    private final FileTransferService sync;
    private final Executor executor;

    public static Builder builder(FileTransferService sync) { return new Builder(sync); }
    private AsyncFileTransferService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    public CompletableFuture<FileMetadata> copyAsync(
            String fileKey, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.copy(fileKey, targetStorageType, targetBucket), executor);
    }

    public CompletableFuture<FileMetadata> moveAsync(
            String fileKey, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.move(fileKey, targetStorageType, targetBucket), executor);
    }

    public CompletableFuture<BatchTransferResult> copyAllAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.copyAll(fileKeys, targetStorageType, targetBucket), executor);
    }

    public CompletableFuture<BatchTransferResult> moveAllAsync(
            Collection<String> fileKeys, Enum<?> targetStorageType, String targetBucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.moveAll(fileKeys, targetStorageType, targetBucket), executor);
    }

    public static final class Builder {
        private final FileTransferService sync;
        private Executor executor = ForkJoinPool.commonPool();
        private Builder(FileTransferService sync) { this.sync = Objects.requireNonNull(sync, "sync"); }
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }
        public AsyncFileTransferService build() { return new AsyncFileTransferService(this); }
    }
}
```

### 2.2 `AsyncFileDeleteService`

```java
public final class AsyncFileDeleteService {
    private final FileDeleteService sync;
    private final Executor executor;

    // ... builder, 생성자 pattern 동일 ...

    public CompletableFuture<Void> deleteAsync(String fileKey) {
        return CompletableFuture.runAsync(() -> sync.delete(fileKey), executor);
    }

    public CompletableFuture<BatchDeleteResult> deleteAllAsync(Collection<String> fileKeys) {
        return CompletableFuture.supplyAsync(() -> sync.deleteAll(fileKeys), executor);
    }
}
```

### 2.3 `AsyncFileRenameService`

```java
public final class AsyncFileRenameService {
    private final FileRenameService sync;
    private final Executor executor;

    // ... builder ...

    public CompletableFuture<FileMetadata> renameAsync(String fileKey, String newName) {
        return CompletableFuture.supplyAsync(() -> sync.rename(fileKey, newName), executor);
    }
}
```

### 2.4 클래스 레벨 JavaDoc 템플릿

```java
/**
 * Async wrapper around {@link XxxService}. See
 * {@linkplain io.github.dornol.filekit.async package docs} for executor
 * selection, exception propagation, and cancellation semantics.
 *
 * @since 0.1.17
 */
```

---

## 3. 테스트 매트릭스

### 3.1 Transfer — 7 케이스

| # | 케이스 |
|---|-------|
| T1 | copyAsync 성공 → metadata 반환 |
| T2 | moveAsync 성공 |
| T3 | copyAllAsync 성공 → batch result |
| T4 | moveAllAsync 성공 |
| T5 | sync가 FileStorageException 던짐 → cause로 전파 |
| T6 | Builder null sync/executor → NPE |
| T7 | 주입된 executor 사용 확인 |

### 3.2 Delete — 5 케이스

| # | 케이스 |
|---|-------|
| D1 | deleteAsync 성공 → CompletableFuture<Void> 완료 |
| D2 | deleteAllAsync 성공 |
| D3 | sync 예외 전파 |
| D4 | Builder 검증 |
| D5 | executor 주입 |

### 3.3 Rename — 4 케이스

| # | 케이스 |
|---|-------|
| R1 | renameAsync 성공 → metadata 반환 |
| R2 | sync 예외 전파 |
| R3 | Builder 검증 |
| R4 | executor 주입 |

---

## 4. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `AsyncFileTransferService` + T1-T7 | 50분 |
| 2 | `AsyncFileDeleteService` + D1-D5 | 30분 |
| 3 | `AsyncFileRenameService` + R1-R4 | 20분 |
| 4 | CHANGELOG | 10분 |
| 5 | 회귀 | 10분 |

총: **약 2시간**

---

## 5. 공개 API

### 추가
- `async.AsyncFileTransferService` + Builder
- `async.AsyncFileDeleteService` + Builder
- `async.AsyncFileRenameService` + Builder

### Breaking
- 없음

---

## 6. Next

`/pdca do async-adapter-expansion`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
