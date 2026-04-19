# Design: Async Adapter (CompletableFuture-based)

> **Plan**: [async-adapter.plan.md](../../01-plan/features/async-adapter.plan.md)
> **Status**: Draft · 2026-04-19

---

## 1. 설계 목표

- Sync 서비스(`FileUploadService`, `FileDownloadService`) 시맨틱을 그대로 유지한 async 래퍼
- kit-core에 새 런타임 의존성 0 (JDK `CompletableFuture`, `Executor`만 사용)
- Java 17 타깃 유지, JDK 21+ Virtual Thread 사용자는 자유롭게 주입 가능
- 원칙: 각 async 메서드는 sync 1:1 대응, executor 한 번만 제출

---

## 2. Plan §2.3 유보 결정 5건 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| Executor 필수 여부 | **기본값 `ForkJoinPool.commonPool()`, 명시 주입 권장** | optional로 유지, JavaDoc에서 명시 주입 강력 권장. 공용 풀 점유는 사용자 선택 |
| `uploadAllAsync` 순차 vs 병렬 | **순차** (sync `uploadAll` 의미 보존). 병렬 batch는 별건 피처 | 의미론 불변 원칙. 병렬화는 dedup/quota 순서에 영향 가능 — 별도 설계 필요 |
| 패키지 이름 | **`async`** | 간결, Java 커뮤니티 관례 (`java.util.concurrent.Future`와 구분) |
| 에러 wrap | **`IOException` → `CompletionException(IOException)`** (CF 표준). `RuntimeException`은 그대로 wrap 없이 `CompletionException.cause`로 노출 | `CompletableFuture` 계약 준수. 사용자는 `.exceptionally(ex -> ex.getCause() instanceof FileStorageException ? ... : ...)` |
| JavaDoc VT 예시 | **클래스 레벨 JavaDoc에 포함** | 실용적 권장. 기본값의 함정 경고와 짝 |

---

## 3. API 정의

### 3.1 `AsyncFileUploadService`

```java
package io.github.dornol.filekit.async;

/**
 * Async wrapper around {@link FileUploadService}. Exposes each public method as
 * a {@link CompletableFuture}-returning variant, submitting to a configurable
 * {@link Executor}.
 *
 * <p><b>Executor choice matters.</b> The default is
 * {@link ForkJoinPool#commonPool()}, which is shared with ambient stream/parallel
 * work — submitting blocking file I/O there can starve other tasks. For
 * production use, inject a dedicated executor. On JDK 21+, prefer virtual
 * threads:
 * <pre>{@code
 * AsyncFileUploadService asyncUpload = AsyncFileUploadService.builder(sync)
 *     .executor(Executors.newVirtualThreadPerTaskExecutor())
 *     .build();
 * }</pre>
 *
 * <p>Checked {@link IOException}s from the sync service are wrapped in
 * {@link CompletionException} per {@code CompletableFuture} conventions.
 * Unchecked exceptions (e.g. {@link FileStorageException}) surface directly
 * as the cause of {@code CompletionException} in consumer callbacks.
 *
 * <p><b>Cancellation note:</b> {@link CompletableFuture#cancel} does not
 * interrupt in-flight I/O; it only marks the future cancelled. Work already
 * submitted to the executor runs to completion.
 *
 * @since 0.1.16
 */
public class AsyncFileUploadService {

    private final FileUploadService sync;
    private final Executor executor;

    public static Builder builder(FileUploadService sync) { return new Builder(sync); }

    private AsyncFileUploadService(Builder b) {
        this.sync = Objects.requireNonNull(b.sync, "sync");
        this.executor = Objects.requireNonNull(b.executor, "executor");
    }

    public CompletableFuture<FileMetadata> uploadAsync(
            FileSource fileSource, Enum<?> storageType, String bucket) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sync.upload(fileSource, storageType, bucket);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<FileMetadata> uploadAsync(
            FileSource fileSource, Enum<?> storageType, String bucket,
            UploadCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sync.upload(fileSource, storageType, bucket, callback);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<BatchUploadResult> uploadAllAsync(
            Collection<? extends FileSource> fileSources,
            Enum<?> storageType, String bucket) {
        return CompletableFuture.supplyAsync(
                () -> sync.uploadAll(fileSources, storageType, bucket),
                executor);
    }

    public static final class Builder {
        private final FileUploadService sync;
        private Executor executor = ForkJoinPool.commonPool();

        private Builder(FileUploadService sync) {
            this.sync = Objects.requireNonNull(sync, "sync");
        }

        /**
         * Sets the executor used to run async operations. Default is
         * {@link ForkJoinPool#commonPool()}; production code should provide
         * a dedicated executor or a virtual-thread executor on JDK 21+.
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public AsyncFileUploadService build() { return new AsyncFileUploadService(this); }
    }
}
```

### 3.2 `AsyncFileDownloadService`

```java
public class AsyncFileDownloadService {

    private final FileDownloadService sync;
    private final Executor executor;

    public static Builder builder(FileDownloadService sync) { return new Builder(sync); }

    private AsyncFileDownloadService(Builder b) { /* requireNonNull pair */ }

    public CompletableFuture<DownloadResult> downloadAsync(String fileKey) {
        return CompletableFuture.supplyAsync(() -> sync.download(fileKey), executor);
    }

    public CompletableFuture<String> resolveUriAsync(String fileKey) {
        return CompletableFuture.supplyAsync(() -> sync.resolveUri(fileKey), executor);
    }

    public CompletableFuture<String> generatePresignedUrlAsync(String fileKey, Duration expiration) {
        return CompletableFuture.supplyAsync(
                () -> sync.generatePresignedUrl(fileKey, expiration), executor);
    }

    public static final class Builder { /* 동일 패턴 */ }
}
```

`download`/`resolveUri`/`generatePresignedUrl`는 `throws` 없으므로 IOException wrap 불필요.

### 3.3 패키지 구조

```
kit-core/src/main/java/io/github/dornol/filekit/
└── async/
    ├── AsyncFileUploadService.java
    ├── AsyncFileDownloadService.java
    └── package-info.java
```

---

## 4. 예외 계약

| 상황 | 사용자가 받는 것 |
|------|----------------|
| sync `IOException` | `CompletionException(IOException)` — `get()` 시 `ExecutionException`로 한 번 더 래핑 가능 |
| sync `FileStorageException` (RuntimeException) | `CompletionException` 의 cause에 그대로 |
| `NullPointerException` (fileKey null 등) | 동일 |
| Builder `null executor` | 즉시 NPE (async 안 감) |

JavaDoc에 패턴 예시:
```java
asyncUpload.uploadAsync(src, type, bucket)
    .exceptionally(ex -> {
        Throwable cause = ex.getCause();  // CompletionException unwrap
        if (cause instanceof FileStorageException fse) {
            log.error("upload failed: {}", fse.getMessageKey());
        }
        return null;
    });
```

---

## 5. 테스트 매트릭스

### 5.1 `AsyncFileUploadServiceTest`

| # | 케이스 | 검증 |
|---|-------|------|
| U1 | `uploadAsync` 성공 → future가 metadata 반환 | `get()` 결과 == sync 결과 |
| U2 | `uploadAsync(callback)` 콜백 전달 | sync mock이 callback 인자 수령 |
| U3 | sync가 `IOException` 던짐 → CompletionException cause가 IOException | `assertThrows(ExecutionException)` + cause chain |
| U4 | sync가 `FileStorageException` 던짐 → cause가 그대로 | 동일 |
| U5 | `uploadAllAsync` 성공 → BatchUploadResult 반환 | 결과 검증 |
| U6 | Builder null sync | NPE |
| U7 | Builder null executor | NPE |
| U8 | Builder 기본 executor = commonPool | 내부 필드 비교 또는 동작으로 간접 검증 |
| U9 | 주입된 executor로 실행 | 특정 스레드에서 실행 확인 (`Thread.currentThread()`) |

### 5.2 `AsyncFileDownloadServiceTest`

| # | 케이스 |
|---|-------|
| D1 | `downloadAsync` 성공 → DownloadResult |
| D2 | `downloadAsync` 에러 — FILE_NOT_FOUND → CompletionException cause |
| D3 | `resolveUriAsync` 성공 |
| D4 | `generatePresignedUrlAsync` 성공 + expiration 전달 |
| D5 | Builder null sync → NPE |
| D6 | Builder null executor → NPE |
| D7 | 주입된 executor에서 실행 |

---

## 6. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `async/package-info.java` + `AsyncFileUploadService` + Builder | 40분 |
| 2 | `AsyncFileUploadServiceTest` U1~U9 | 60분 |
| 3 | `AsyncFileDownloadService` + Builder | 25분 |
| 4 | `AsyncFileDownloadServiceTest` D1~D7 | 50분 |
| 5 | JavaDoc — VT 예시 + 예외 체인 | 30분 |
| 6 | CHANGELOG | 10분 |
| 7 | 회귀 | 10분 |

총 예상: **약 3.7시간**

---

## 7. 공개 API 변경

### 추가
- `io.github.dornol.filekit.async.AsyncFileUploadService` + Builder
- `io.github.dornol.filekit.async.AsyncFileDownloadService` + Builder
- `package-info.java`

### Breaking
- 없음

---

## 8. Next Steps

1. [ ] `/pdca do async-adapter`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
