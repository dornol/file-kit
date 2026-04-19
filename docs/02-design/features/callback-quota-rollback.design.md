# Design: Callback & Save Failure Cleanup + Quota Rollback Hook

> **Summary**: 업로드 후단 실패(callback/save) 경로 정합성 강화 — save 실패 시 storage 정리 + `onUploadFailed` 이벤트 추가
>
> **Project**: file-kit
> **Version**: 0.1.10 → 0.1.14 (예정)
> **Author**: dhkim
> **Date**: 2026-04-19
> **Status**: Draft
> **Plan**: [callback-quota-rollback.plan.md](../../01-plan/features/callback-quota-rollback.plan.md)

---

## 1. 설계 목표

- `storage.upload` 성공 후 실패 모드를 **모두** 관찰 가능하게
- save 실패 시 storage orphan 방지 (버그 수정)
- 앱이 보상 로직을 **이벤트 리스너**로 통합 구현 가능 (try-catch 보일러플레이트 제거)
- 공개 API breaking 0 (default method + 신규 public 메서드만)

### 설계 원칙

- 기존 `FileEventPublisher` + `FileEventListener` 패턴 확장. 새 SPI 불도입
- 실패 이벤트는 storage 정리 **이후** 발화 (observer가 "이미 정리됐음" 가정 가능)
- 리스너 예외는 기존 `dispatch` 패턴대로 swallow + WARN
- cause는 `Throwable` — DB save 실패(SQLException 등)는 wrapping하지 않고 그대로 전파

---

## 2. Plan §2.3 유보 결정 확정 (5/5)

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 이벤트 cause 타입 | **`Throwable`** | save 실패는 repo 구현체가 던지는 DB 예외 (SQLException, DataAccessException 등). file-kit이 wrapping하면 정보 손실. 리스너는 `instanceof FileStorageException`로 분기 가능 |
| metadata 계약 | **"in-memory 인스턴스, repository 미반영 가능" JavaDoc 명시** | save 실패 경로의 metadata는 `new FileMetadata(...)`로 만들어졌고 DB에 없음. 리스너가 `metadataRepository.getByKey(metadata.key())` 호출하면 not-found. 명시적 문서화 필요 |
| storage.delete 자체 실패 | **`primary.addSuppressed(cleanupEx)` + WARN 로그** | 표준 Java 관례. 원 예외 정보 보존하면서 cleanup 실패도 진단 가능 |
| 이벤트 발화 시점 | **storage.delete 수행 후, 원 예외 throw 전** | 리스너가 "이미 정리됨" 가정 가능. delete 실패 시에도 fire (suppressed로 원인 포함) |
| callback vs save 실패 구분 | **cause 타입 기반. 별도 enum 없음** | `FileStorageException.getMessageKey() == CALLBACK_FAILED`으로 callback 식별. save 실패는 그 외 모든 cause. 문서에 구분 패턴 예시 기재 |

### 2.1 이벤트 의미 고정

```
FileEventListener.onUploadFailed(metadata, cause) 발화 조건:
  - storage.upload() 성공 이후에 발생한 실패
  - storage.delete(metadata)가 이미 시도됨 (성공 or 실패)
  - cause는:
    · callback 실패 → FileStorageException(CALLBACK_FAILED), 원인은 getCause()
    · save 실패 → repo가 던진 예외 그대로 (wrapping 안 함)
  - delete 자체 실패 시 cause.getSuppressed()에 cleanup 예외 포함
```

---

## 3. API 정의

### 3.1 `FileEventListener` 확장

```java
public interface FileEventListener {
    // 기존 메서드 유지...

    /**
     * Called when an upload fails <b>after</b> the file content was successfully
     * written to storage. By the time this fires, file-kit has already attempted
     * to delete the file from storage (success or failure; see below).
     *
     * <p>Common triggers:
     * <ul>
     *   <li>The {@link io.github.dornol.filekit.upload.UploadCallback} threw —
     *       {@code cause} will be a {@link io.github.dornol.filekit.storage.FileStorageException}
     *       with {@link io.github.dornol.filekit.storage.FileStorageException#CALLBACK_FAILED}.
     *       Its {@link Throwable#getCause()} is the original callback exception.</li>
     *   <li>{@link io.github.dornol.filekit.spi.FileMetadataRepository#save} threw —
     *       {@code cause} is the repository exception as-is (e.g. {@code SQLException},
     *       {@code DataAccessException}). Not wrapped by file-kit.</li>
     * </ul>
     *
     * <p>If storage cleanup itself failed, {@code cause.getSuppressed()} contains
     * the cleanup exception.
     *
     * <p><b>metadata is NOT persisted.</b> It is the in-memory instance that
     * would have been saved. Attempting
     * {@link io.github.dornol.filekit.spi.FileMetadataRepository#getByKey}
     * with {@code metadata.key()} will throw not-found.
     *
     * <p><b>Do NOT delete storage again.</b> file-kit has already attempted it;
     * a second delete will return ENOENT / no-op at best. Typical uses:
     * decrement external quota counter, record audit log, emit failure metric.
     *
     * <p>Exceptions thrown from this method are swallowed (logged at WARN).
     *
     * @since 0.1.14
     */
    default void onUploadFailed(FileMetadata metadata, Throwable cause) {}
}
```

### 3.2 `FileEventPublisher` 확장

```java
public void fireUploadFailed(FileMetadata metadata, Throwable cause) {
    dispatch("onUploadFailed", listener -> listener.onUploadFailed(metadata, cause));
}
```

파일 하단에 추가. 기존 `dispatch` 재사용.

### 3.3 `FileUploadService` 변경

**현재 (L316-322)**:
```java
executeCallback(callback, metadata, storage);    // CALLBACK_FAILED 내부에서 storage.delete 수행
FileMetadata saved = metadataRepository.save(metadata);  // ← 실패 시 orphan
log.info("File uploaded: ...");
eventPublisher.fireUploaded(saved);
return saved;
```

**변경**:
```java
try {
    executeCallback(callback, metadata, storage);
} catch (FileStorageException cbFailure) {
    // executeCallback이 이미 storage.delete 수행
    eventPublisher.fireUploadFailed(metadata, cbFailure);
    throw cbFailure;
}

FileMetadata saved;
try {
    saved = metadataRepository.save(metadata);
} catch (Exception saveFailure) {
    cleanupStorageBestEffort(storage, metadata, saveFailure);
    eventPublisher.fireUploadFailed(metadata, saveFailure);
    throw saveFailure;
}

log.info("File uploaded: key={}, size={}, bucket={}, storageType={}",
        saved.key(), saved.size(), bucket, storageType);
eventPublisher.fireUploaded(saved);
return saved;
```

**신규 helper** (private static):
```java
private static void cleanupStorageBestEffort(FileStorage storage,
                                             FileMetadata metadata,
                                             Throwable primary) {
    try {
        storage.delete(metadata);
    } catch (Exception cleanupEx) {
        log.warn("Storage cleanup failed after upload failure for key={}: {}",
                metadata.key(), cleanupEx.getMessage());
        primary.addSuppressed(cleanupEx);
    }
}
```

### 3.4 JavaDoc 업데이트 — `upload(... callback)`

현 L210-216의 "Quota note" 문단 교체:

```
<p><strong>Failure handling:</strong> If the callback throws or metadata
persistence fails, the uploaded file is deleted from storage and
{@link FileEventListener#onUploadFailed} fires. For external bookkeeping
(quota counters, audit logs, metrics) the recommended pattern is to
subscribe to that event instead of catching
{@link FileStorageException#CALLBACK_FAILED} around the upload call —
a single listener path handles both callback and save failures.</p>

<p>If the {@link io.github.dornol.filekit.quota.QuotaChecker} was configured
with a counter-based {@link io.github.dornol.filekit.spi.QuotaUsageProvider}
(one that independently tracks usage rather than reading from storage/metadata),
decrement the counter inside {@code onUploadFailed}.</p>
```

---

## 4. 예외 계약

| 상황 | `cause` 타입 | getSuppressed | 발화 순서 |
|------|------------|---------------|---------|
| callback 예외 + storage.delete 성공 | `FileStorageException(CALLBACK_FAILED)`, `.getCause()` = 원 예외 | 비어있음 | storage.delete → fire → throw |
| callback 예외 + storage.delete 실패 | 동일 | delete 예외 포함 (executeCallback 측 변경 필요 여부 검토) | 동일 |
| save 예외 + storage.delete 성공 | repo 예외 그대로 | 비어있음 | storage.delete → fire → throw |
| save 예외 + storage.delete 실패 | repo 예외 그대로 | cleanup 예외 포함 | storage.delete 시도 → fire → throw |
| 리스너 예외 | — | — | 기존 dispatch swallow + WARN |

### 4.1 `executeCallback`의 storage.delete 실패 처리

현재 `executeCallback`:
```java
private static void executeCallback(@Nullable UploadCallback callback,
                                    FileMetadata metadata, FileStorage storage) {
    if (callback == null) return;
    try {
        callback.onUploaded(metadata);
    } catch (Exception e) {
        storage.delete(metadata);  // ← 여기서 예외 시 처리 없음
        throw new FileStorageException(FileStorageException.CALLBACK_FAILED,
                "Upload callback failed, file has been deleted: " + metadata.key(), e);
    }
}
```

**개선**: delete 자체 실패를 suppressed로 기록하도록 수정.

```java
private static void executeCallback(@Nullable UploadCallback callback,
                                    FileMetadata metadata, FileStorage storage) {
    if (callback == null) return;
    try {
        callback.onUploaded(metadata);
    } catch (Exception e) {
        FileStorageException wrapped = new FileStorageException(FileStorageException.CALLBACK_FAILED,
                "Upload callback failed, file has been deleted: " + metadata.key(), e);
        try {
            storage.delete(metadata);
        } catch (Exception cleanupEx) {
            log.warn("Storage cleanup failed after callback failure for key={}: {}",
                    metadata.key(), cleanupEx.getMessage());
            wrapped.addSuppressed(cleanupEx);
        }
        throw wrapped;
    }
}
```

메시지는 기존 그대로 유지 ("file has been deleted") — delete 실패 시에도 문구 유지(보상은 suppressed로). 정확성을 더 원하면 향후 메시지 분기, 현 단계는 범위 밖.

---

## 5. 테스트 매트릭스

### 5.1 신규 케이스 (`FileUploadServiceTest`)

| # | 케이스 | 검증 |
|---|-------|------|
| U1 | 정상 upload → `onUploadFailed` 미호출 | `verify(listener, never()).onUploadFailed(any(), any())` |
| U2 | callback 실패 → `onUploadFailed(metadata, FileStorageException(CALLBACK_FAILED))` 발화 | 리스너 호출 캡처 + cause.getMessageKey() 확인 |
| U3 | callback 실패 → `onUploadFailed`가 `storage.delete` 이후 호출됨 | InOrder 검증 |
| U4 | save 실패 → `storage.delete(metadata)` 호출됨 | `verify(storage).delete(metadata)` |
| U5 | save 실패 → `onUploadFailed(metadata, saveException)` 발화 | cause가 save 예외 그대로 |
| U6 | save 실패 → upload가 save 예외 그대로 전파 (wrap 없이) | `assertThrows(SpecificException)` |
| U7 | callback 실패 + storage.delete 실패 → 원 예외의 suppressed에 cleanup 예외 기록 | `assertTrue(getSuppressed().length > 0)` |
| U8 | save 실패 + storage.delete 실패 → 원 예외 suppressed에 cleanup 예외 기록 | 동일 |
| U9 | 리스너가 `onUploadFailed` 내부에서 예외 → 원 예외 전파 방해 안 함 | 원 예외 타입 전파 확인 |
| U10 | callback 없는 upload에서 save 실패 → `onUploadFailed` 발화 + storage cleanup | callback 경로와 독립적으로 동작 |
| U11 | dedup hit 경로 → `onUploadFailed` 미호출 | dedup 시 save/callback 아예 스킵 |

### 5.2 기존 테스트 회귀

- 기존 CALLBACK_FAILED catch 테스트 유지 — 이벤트 추가가 예외 전파 동작을 변경하지 않음을 확인
- `FileUploadServiceTest` 1197건 전체 회귀 0

---

## 6. 구현 순서

| 단계 | 작업 | 파일 | 예상 |
|------|------|------|------|
| 1 | `FileEventListener.onUploadFailed` default 추가 | `spi/FileEventListener.java` | 10분 |
| 2 | `FileEventPublisher.fireUploadFailed` 추가 | `event/FileEventPublisher.java` | 10분 |
| 3 | `FileUploadService.doUpload` save try/catch + cleanup helper + 이벤트 발화 | `upload/FileUploadService.java` | 30분 |
| 4 | `executeCallback`의 cleanup suppressed 처리 + 이벤트 발화 | 동일 | 20분 |
| 5 | `FileUploadServiceTest` U1~U11 추가 | `test/upload/` | 70분 |
| 6 | JavaDoc 업데이트 (upload 메서드 + listener) | 여러 파일 | 25분 |
| 7 | CHANGELOG `[Unreleased]` | 기존 | 10분 |
| 8 | 회귀 테스트 | CI | 10분 |

총 예상: **약 3시간**

---

## 7. 공개 API 변경 요약

### 추가
- `FileEventListener.onUploadFailed(FileMetadata, Throwable)` default method
- `FileEventPublisher.fireUploadFailed(FileMetadata, Throwable)` public method

### 변경 (내부)
- `FileUploadService.doUpload` save 경로 — orphan 방지 (기존 동작은 예외 전파만, 이제 cleanup도)
- `FileUploadService.executeCallback` — cleanup 실패를 suppressed로 기록

### Breaking
- 없음 (default method, 기존 리스너 수정 불필요. save 실패 시 cleanup은 추가 동작, 기존 에러 전파 동작은 유지)

### 마이그레이션 노트
- 기존 `try { upload(..., callback) } catch(FileStorageException cb) { if(cb.getMessageKey() == CALLBACK_FAILED) decrementCounter() }` 코드는 동작 유지
- 권장: `FileEventListener` 구현에 `onUploadFailed(metadata, cause)` 오버라이드. callback/save 양쪽 통합 처리
- save 실패 시 예외 타입은 기존과 동일 (wrap 변화 없음)

---

## 8. 위험 재평가

| 위험 | 완화 |
|------|------|
| save 실패 cleanup이 느린 storage (S3 등)에서 시간 길어짐 | best-effort로 block — 현재 설계 한계, 비동기화는 별건 |
| 리스너가 외부 시스템 호출 시 실패 전파 지연 | dispatch swallow 유지, WARN 로그만 |
| 대량 listener 등록 시 fire 비용 | 기존 패턴 유지, 문제화되면 비동기 dispatch 별건 |
| metadata 미저장 상태를 리스너가 몰라 repo 조회 시도 | JavaDoc 강조 + 필요시 README 예시 |

---

## 9. Next Steps

1. [ ] `/pdca do callback-quota-rollback` — §6 8단계 순서대로 구현
2. [ ] `/pdca analyze callback-quota-rollback`
3. [ ] `/pdca report callback-quota-rollback`

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
