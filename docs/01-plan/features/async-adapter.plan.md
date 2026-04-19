# Plan: Async Adapter (CompletableFuture-based)

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | async-adapter |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `AsyncFileUploadService` + `AsyncFileDownloadService` — 기존 sync 서비스를 `CompletableFuture` 기반으로 래핑 |
| Related | 초기 리뷰 A3. JDK 17 타깃 유지 (Virtual Thread는 사용자 주입) |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `FileUploadService`/`FileDownloadService`는 blocking. 서버 애플리케이션이 async 흐름(리액티브, 코루틴, VT 기반 웹)에서 사용하려면 `supplyAsync` boilerplate가 호출부마다 반복됨 |
| **Solution** | `AsyncFileUploadService`, `AsyncFileDownloadService` — 동일 public 메서드를 `CompletableFuture<T>` 반환으로 제공. `Executor` 주입 가능 (기본 `ForkJoinPool.commonPool()`). JDK 21+ 사용자는 `Executors.newVirtualThreadPerTaskExecutor()` 주입 가능 |
| **Function/UX Effect** | async 호출부 `CompletableFuture.supplyAsync(() -> sync.upload(...), executor)` → `async.upload(...)` 한 줄 |
| **Core Value** | 기존 sync API는 유지하면서 async 통합을 1급 지원. kit-core 순수 Java 원칙 유지 (Spring·리액티브 프레임워크 의존 0) |

---

## 1. 배경

### 1.1 현황

- 모든 서비스(`FileUploadService` 등)가 blocking I/O
- 사용자가 async 흐름에서 쓰려면 `CompletableFuture.supplyAsync(() -> service.method(...), myExecutor)` 반복
- executor 지정 잊으면 `ForkJoinPool.commonPool()` (blocking I/O가 공용 풀 점유 — 안티패턴)

### 1.2 타깃 JDK

- 현재 `sourceCompatibility = 17` (kit-core/build.gradle.kts:8)
- Virtual Thread(Java 21+)는 kit-core에서 **하드코딩하지 않음**. `Executor` 주입 지점만 제공
- JDK 21+ 사용자는 자신의 `Executors.newVirtualThreadPerTaskExecutor()` 주입. 문서에 권장 명시

### 1.3 범위 결정

- **이번 사이클**: Upload + Download 2개 서비스
- 나머지(Transfer/Delete/Rename)는 패턴 확립 후 별건. Upload/Download이 80% 이상 async 수요 커버
- `uploadAllAsync` 같은 배치 병렬화는 **별건** — 현 sync `uploadAll`은 순차라 의미론 다름

---

## 2. 범위

### 2.1 In Scope

- [ ] 신규 패키지 `io.github.dornol.filekit.async` (kit-core 내부)
- [ ] `AsyncFileUploadService` — wraps `FileUploadService`, exposes async 메서드
  - `uploadAsync(FileSource, Enum<?>, String)` → `CompletableFuture<FileMetadata>`
  - `uploadAsync(FileSource, Enum<?>, String, UploadCallback)` → `CompletableFuture<FileMetadata>`
  - `uploadAllAsync(Collection<FileSource>, Enum<?>, String)` → `CompletableFuture<BatchUploadResult>` (내부는 순차, 병렬화는 별건)
- [ ] `AsyncFileDownloadService` — wraps `FileDownloadService`
  - `downloadAsync(String)` → `CompletableFuture<DownloadResult>`
  - `resolveUriAsync(String)` → `CompletableFuture<String>`
  - `generatePresignedUrlAsync(String, Duration)` → `CompletableFuture<String>`
- [ ] Builder 패턴: `AsyncFileUploadService.builder(sync).executor(myExecutor).build()`
- [ ] 기본 executor: `ForkJoinPool.commonPool()` (명시적 선택은 권장, 경고 로그는 하지 않음)
- [ ] 단위 테스트 각 서비스
- [ ] JavaDoc — VT 권장 패턴 예시
- [ ] CHANGELOG

### 2.2 Out of Scope

- `AsyncFileTransferService` / `AsyncFileDeleteService` / `AsyncFileRenameService` (패턴만 확립, 다음 사이클)
- **병렬 batch** (`uploadAllAsync`이 각 파일을 병렬 제출) — 의미론 변화, 별건
- Spring `ReactiveFileKit` 래퍼 (kit-spring-boot-starter 확장, 별건)
- Cancellation propagation (`CompletableFuture.cancel()`이 진행 중 I/O를 끊는 방식) — JDK 제약 (interrupt 기반), 추가 설계 필요

### 2.3 Design 단계 유보 결정

- **Builder가 null executor 허용? 또는 필수?** — 기본값 있으면 optional, 없으면 required
- **`uploadAllAsync`의 순차 vs 병렬** — 이번엔 순차 고정, 병렬은 별건. 문서화 확정
- **패키지 이름**: `async` vs `concurrent` vs `future`
- **에러 전파 문서화**: `CompletableFuture.exceptionally`/`handle`에서 어떤 예외 타입이 노출되는지 (IOException wrapping)
- **Javadoc에 VT 권장 예시 명시 여부**

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | 각 async 메서드는 sync 메서드와 시그니처 대칭, 반환만 `CompletableFuture<T>` | High |
| FR-02 | sync 예외는 `CompletableFuture`의 `completeExceptionally`로 전달 | High |
| FR-03 | checked `IOException`은 `CompletionException`으로 wrap (CompletableFuture 표준 동작) | High |
| FR-04 | Builder에 `executor(Executor)` — null 전달 시 NPE | High |
| FR-05 | Builder에 기본 executor — `ForkJoinPool.commonPool()` | High |
| FR-06 | 생성자 `null sync` → NPE | High |
| FR-07 | 각 async 메서드 내부에서 `executor`가 1회만 호출되도록 (중복 submit 없음) | Medium |
| FR-08 | Sync 서비스의 공개 API와 100% 시맨틱 동등 (dedup hit, 이벤트, 예외, etc.) | High |

### 3.2 비기능

- kit-core 새 의존성 0
- breaking 0
- 기존 테스트 회귀 0

---

## 4. 설계 개요

### 4.1 `AsyncFileUploadService` (스케치)

```java
public class AsyncFileUploadService {
    private final FileUploadService sync;
    private final Executor executor;

    public static Builder builder(FileUploadService sync) { return new Builder(sync); }

    private AsyncFileUploadService(Builder b) { /* requireNonNull */ }

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

    // ... 나머지 오버로드 + uploadAllAsync ...

    public static final class Builder {
        private final FileUploadService sync;
        private Executor executor = ForkJoinPool.commonPool();

        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public AsyncFileUploadService build() { return new AsyncFileUploadService(this); }
    }
}
```

### 4.2 `AsyncFileDownloadService` — 동일 패턴 (DownloadResult 반환 포함)

### 4.3 테스트 전략

- Mockito로 sync 서비스 stub → async 래퍼가 결과를 그대로 전파하는지 검증
- `CompletableFuture#get()`으로 결과 수집
- 예외 전파: sync가 `FileStorageException` 던지면 `.exceptionally()`에서 동일 타입 받음
- executor 주입: 테스트에서 `Executors.newSingleThreadExecutor()` 사용, 사용 후 shutdown

---

## 5. 성공 기준

### 5.1 DoD

- [ ] FR-01~08 구현
- [ ] `AsyncFileUploadServiceTest`, `AsyncFileDownloadServiceTest` 각 ~8~10 케이스
- [ ] 기존 테스트 회귀 0
- [ ] README 또는 JavaDoc에 사용 예시 1개 (VT 주입 포함)
- [ ] CHANGELOG

### 5.2 품질

- [ ] `./gradlew build` 성공
- [ ] 공개 API breaking 0

---

## 6. 위험 및 완화

| 위험 | 영향 | 가능성 | 완화 |
|------|------|-------|------|
| blocking I/O가 공용 풀 점유 (사용자가 executor 미지정) | High | Medium | Builder 기본값 `ForkJoinPool.commonPool()` 유지하되 JavaDoc에 "blocking I/O는 전용 풀 또는 VT 권장" 경고 |
| `CompletableFuture.cancel`이 실제 I/O 중단 안 함 | Medium | High (정적 제약) | JavaDoc에 "cancel은 결과 폐기만, 실행은 완료" 명시 |
| async에서 thread-local 의존 코드 (Spring `SecurityContextHolder` 등) 깨짐 | High | Medium | kit-core는 thread-local 없음, 사용자 콜백 책임 범위 |
| `CompletionException` vs 원 예외 혼동 | Medium | Medium | 문서에 `cause.getCause()` 체인 예시 |

---

## 7. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `async/` 패키지 + `AsyncFileUploadService` + Builder | 40분 |
| 2 | `AsyncFileUploadServiceTest` ~8 케이스 | 60분 |
| 3 | `AsyncFileDownloadService` + Builder | 30분 |
| 4 | `AsyncFileDownloadServiceTest` ~8 케이스 | 60분 |
| 5 | JavaDoc (VT 예시 포함) | 30분 |
| 6 | CHANGELOG | 10분 |
| 7 | 회귀 확인 | 10분 |

총 예상: **약 4시간**

---

## 8. 공개 API 변경

### 추가
- `io.github.dornol.filekit.async.AsyncFileUploadService`
- `io.github.dornol.filekit.async.AsyncFileDownloadService`
- 각 Builder

### Breaking
- 없음

### 마이그레이션 노트
- 기존 sync API 그대로 사용 가능
- async 통합 원할 시:
  ```java
  AsyncFileUploadService asyncUpload = AsyncFileUploadService.builder(syncUpload)
      .executor(Executors.newVirtualThreadPerTaskExecutor())  // JDK 21+
      .build();
  ```

---

## 9. Next Steps

1. [ ] `/pdca design async-adapter` — 유보 결정 5건 확정

---

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
