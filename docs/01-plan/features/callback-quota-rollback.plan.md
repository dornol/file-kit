# Plan: Callback & Save Failure Cleanup + Quota Rollback Hook

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | callback-quota-rollback |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `FileUploadService` failure-path cleanup + new `onUploadFailed` event. Quota 롤백은 이벤트 훅으로 제공 (file-kit은 상태를 갖지 않음) |
| Related | `docs/review/2026-04-19-library-review.md` (R4). 조사 중 발견된 추가 이슈 (R4.1: save 실패 시 storage orphan) 포함 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | 업로드 후단 실패(post-`storage.upload`)의 정합성이 불완전. (1) 콜백 실패는 storage 정리만 수행, 앱이 quota 카운터를 가진 경우 직접 보상 로직을 짜야 함. (2) `metadataRepository.save` 실패 시 **storage orphan 발생** — cleanup 경로 없음. (3) 두 실패 모두 이벤트 없음 → 앱이 hook할 지점 없음 |
| **Solution** | (a) save 실패 시 storage 파일 삭제로 orphan 방지. (b) `onUploadFailed(metadata, cause)` 이벤트 추가 — 콜백 실패/저장 실패 양쪽에서 발화. 앱은 이 이벤트를 구독해 counter-based quota 보상, audit log, 메트릭 등 수행 가능. (c) JavaDoc 명확화 + 샘플 리스너 예시 |
| **Function/UX Effect** | Orphan 스토리지 발생 루트 하나 제거. 앱의 quota 롤백 코드가 `try { upload() } catch(CALLBACK_FAILED)`에서 `FileEventListener.onUploadFailed`로 깔끔하게 이동 가능 |
| **Core Value** | 업로드 실패 모드가 "정리됨 + 앱에 관찰 가능"한 상태로 통일. 기존 `fireUploaded`/`fireDeleted` 이벤트 패턴과 대칭 |

---

## 1. 배경

### 1.1 현황 — post-storage 실패 모드 3종

`FileUploadService.doUpload()`의 storage.upload 성공 후 발생할 수 있는 실패:

| # | 실패 지점 | 현 동작 | 스토리지 정리 | 이벤트 | 앱 보상 가능? |
|---|----------|--------|:---:|:---:|:---:|
| 1 | `executeCallback` (L316) | `storage.delete(metadata)` → `throw CALLBACK_FAILED` | ✅ | ❌ | 예외 catch 필요 |
| 2 | `metadataRepository.save` (L318) | **아무 정리 없음** → 예외 전파 | ❌ **orphan** | ❌ | **불가** (file-kit이 metadata를 모름) |
| 3 | `eventPublisher.fireUploaded` (L321) | 내부 swallow + WARN 로그 | — | — | — |

**#2가 리뷰에 빠진 실제 버그**. save 실패(DB unique 제약, connection drop 등) 시 storage에 파일이 남고, 다음 업로드 dedup 체크도 통과하지 못함 (metadata가 없으므로 `findByChecksum` null).

### 1.2 원래 R4 재해석

리뷰의 R4 문구는 "콜백 실패 시 quota 롤백 부재"였으나, 코드 조사 결과:

- `QuotaChecker.check()`는 `QuotaUsageProvider`의 pure read. file-kit 쪽에 roll back할 상태 없음
- 앱이 counter-based provider를 쓰는 경우에만 "롤백"이 의미 있음. 현재 JavaDoc이 "앱이 직접 해야 한다"고 기재 (L210-216)

→ **실제 필요한 것은 file-kit이 quota를 추적하는 것이 아니라, 앱이 hook할 수 있는 지점을 제공하는 것**. 따라서 본 피처는:

- (a) save 실패 orphan 수정 (R4.1, 신규)
- (b) `onUploadFailed` 이벤트 추가 (R4 본질)

두 축으로 재정의.

### 1.3 Quota 관점 구체 시나리오

앱이 counter-based `QuotaUsageProvider`를 쓸 때 현재 동작:

```
현재: app가 upload() 호출 → CALLBACK_FAILED catch → counter 수동 decrement

제안: app가 upload() 호출 → onUploadFailed(metadata, cause) 리스너 → counter decrement
```

리스너 기반이면 try-catch 보일러플레이트 제거. 여러 origin(callback/save)에서 동일 이벤트로 통일.

---

## 2. 범위

### 2.1 In Scope

- [ ] `metadataRepository.save` 실패 시 `storage.delete(metadata)` 보상 (orphan 방지)
- [ ] `FileEventListener.onUploadFailed(FileMetadata, Throwable cause)` default method 추가
- [ ] `FileEventPublisher.fireUploadFailed(metadata, cause)` 메서드 추가
- [ ] `FileUploadService.doUpload()` 실패 경로에서 이벤트 발화
- [ ] 이벤트 발화는 callback 실패 / save 실패 두 경로에서 호출
- [ ] JavaDoc 업데이트 — "onUploadFailed 구독이 CALLBACK_FAILED catch 대체 권장 경로"
- [ ] 신규/수정 테스트
- [ ] CHANGELOG

### 2.2 Out of Scope

- `QuotaPolicy`/`QuotaUsageProvider` SPI에 `reserve`/`commit`/`release` 추가 (2-phase commit 패턴) — 설계 복잡도 대비 가치 경계, 필요시 별건
- Virus scan/encrypt 실패 시 이벤트 (이미 storage에 파일 쓰기 전 단계라 orphan 없음, 이벤트 추가 이익 낮음)
- `fireUploadFailed` 비동기화 (기존 fire 패턴과 동일한 synchronous fire-and-forget 유지)
- `BatchUploadResult`에 failure reason 추가 (별건 R6 범위)

### 2.3 Design 단계 유보 결정

- **이벤트 시그니처**: `(FileMetadata metadata, Throwable cause)` vs `(FileMetadata metadata, FileStorageException cause)` — 타입 좁힐지 넓힐지
- **metadata.location 정확성**: save 실패 시 metadata는 `new FileMetadata(...)`로 생성된 인스턴스 (아직 저장 전), `location`은 실제 storage 위치. 이벤트에서 이 metadata 전달 OK인지 — 리스너가 `save()`되지 않은 metadata 받는 걸 명시적으로 알아야 함
- **두 번의 delete 시도**: callback 실패 시 storage.delete → 이벤트 → 앱이 또 delete 시도하면 ENOENT. 문서로 "리스너는 storage 재삭제 금지" 명시
- **이벤트 발화 순서**: storage.delete 전 vs 후 — 후가 맞음 (cleanup 완료 후 통지)
- **CALLBACK_FAILED / SAVE failure 원인 구분**: cause 예외 타입으로 구분 vs 별도 reason enum

---

## 3. 요구사항

### 3.1 기능 요구사항

| ID | 요구사항 | 우선순위 | 상태 |
|----|---------|---------|------|
| FR-01 | `metadataRepository.save` 실패 시 `storage.delete(metadata)` 호출 | High | Pending |
| FR-02 | save 실패 원인 예외는 wrapping 없이 그대로 전파 (기존 동작 유지) | High | Pending |
| FR-03 | `FileEventListener.onUploadFailed(metadata, cause)` default method로 추가 (기존 리스너 호환) | High | Pending |
| FR-04 | callback 실패 경로에서 storage.delete 후 `fireUploadFailed` 발화 | High | Pending |
| FR-05 | save 실패 경로에서 storage.delete 후 `fireUploadFailed` 발화 | High | Pending |
| FR-06 | 리스너 예외는 기존 `dispatch` 패턴대로 swallow + WARN | High | Pending |
| FR-07 | storage.delete 자체가 실패하면 WARN 로그 + cause에 suppressed로 기록, 원예외 계속 전파 | Medium | Pending |
| FR-08 | 정상 upload 경로는 `onUploadFailed` 미호출 (회귀 테스트) | High | Pending |

### 3.2 비기능 요구사항

| 항목 | 기준 | 측정 |
|------|------|------|
| 공개 API breaking | 0 (default method 이용) | `./gradlew build` |
| 기존 테스트 회귀 | 0 실패 | CI |
| 문서화 | JavaDoc에 권장 사용 패턴 예시 포함 | 리뷰 |

---

## 4. 설계 개요

### 4.1 `FileEventListener` 확장

```java
public interface FileEventListener {
    // 기존 메서드들...
    default void onUploaded(FileMetadata metadata) {}
    default void onDownloaded(FileMetadata metadata) {}
    // ...

    /**
     * Called when an upload fails after the content was written to storage.
     * The file has already been removed from storage when this fires — listeners
     * should NOT attempt to delete it again. Typical uses: decrement quota
     * counter, record audit log, emit failure metric.
     */
    default void onUploadFailed(FileMetadata metadata, Throwable cause) {}
}
```

### 4.2 `FileEventPublisher` 확장

```java
public void fireUploadFailed(FileMetadata metadata, Throwable cause) {
    dispatch("onUploadFailed", listener -> listener.onUploadFailed(metadata, cause));
}
```

### 4.3 `FileUploadService.doUpload` 수정

**현재 (L316-322)**:
```java
executeCallback(callback, metadata, storage);  // CALLBACK_FAILED 시 내부에서 storage.delete

FileMetadata saved = metadataRepository.save(metadata);  // 실패 시 orphan
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

log.info("File uploaded: ...");
eventPublisher.fireUploaded(saved);
return saved;
```

`cleanupStorageBestEffort`:
```java
private void cleanupStorageBestEffort(FileStorage storage, FileMetadata metadata, Throwable primary) {
    try {
        storage.delete(metadata);
    } catch (Exception cleanupEx) {
        log.warn("Storage cleanup failed for key={} after upload failure: {}",
                metadata.key(), cleanupEx.getMessage());
        primary.addSuppressed(cleanupEx);
    }
}
```

(`executeCallback`은 기존 구조 유지 — `storage.delete` 수행 후 FileStorageException 던짐. 신규 cleanup 메서드는 save 실패 전용.)

### 4.4 JavaDoc 업데이트

`FileUploadService.upload(... callback)` JavaDoc의 "Quota note" 섹션 교체:

```
<p><strong>Failure handling:</strong> If the callback throws or metadata
persistence fails, the uploaded file is deleted from storage and
{@link FileEventListener#onUploadFailed} fires. Apps maintaining
external counters (e.g. quota) should subscribe to this event rather
than catching {@link FileStorageException#CALLBACK_FAILED} manually.</p>
```

---

## 5. 성공 기준

### 5.1 Definition of Done

- [ ] FR-01~08 구현
- [ ] 신규 테스트:
  - save 실패 시 storage.delete 호출 확인
  - save 실패 시 onUploadFailed 발화 확인
  - callback 실패 시 onUploadFailed 발화 확인 (storage.delete 후)
  - 정상 upload 경로는 onUploadFailed 미호출
  - 리스너 예외가 원 예외 전파 방해하지 않음
  - storage.delete 실패 시 suppressed로 기록, 원 예외는 그대로
- [ ] 기존 `FileUploadServiceTest` 회귀 0
- [ ] CHANGELOG `[Unreleased]` 엔트리

### 5.2 품질 기준

- [ ] `./gradlew build` 성공
- [ ] 공개 API breaking 0 (FileEventListener default method)

---

## 6. 위험 및 완화

| 위험 | 영향 | 가능성 | 완화 |
|------|------|-------|------|
| 리스너가 `onUploadFailed`에서 storage 재삭제 시도 (ENOENT) | Low | Medium | JavaDoc에 "재삭제 금지" 명시, 앱은 quota/audit만 수행 |
| `onUploadFailed` 발화 중 리스너 예외가 원 예외 대체 | Medium | Low | `dispatch`의 기존 swallow + WARN 패턴 유지 |
| save 재시도 중복 처리 — save 실패 후 리스너가 counter decrement, 이후 retry 성공 시 2번째 save에서 이중 계산 | Medium | Low | 리스너 수준에서는 "fire-and-forget, 재시도는 호출자 책임" 명확화 |
| 기존 `CALLBACK_FAILED` catch 사용 코드가 있다면 이벤트로 마이그레이션 권고만 (둘 다 동작) | Low | Low | CHANGELOG에 deprecation 없이 "recommended path" 안내 |
| FileMetadata가 아직 save 안 된 상태로 이벤트 전달 — 리스너가 DB 조회 등 시도하면 찾을 수 없음 | Medium | Medium | JavaDoc 명시: "metadata는 in-memory 인스턴스, DB에 존재하지 않음" |

---

## 7. 구현 순서 (예상)

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `FileEventListener.onUploadFailed` default + `FileEventPublisher.fireUploadFailed` | 20분 |
| 2 | `FileUploadService.doUpload` 수정 — save try/catch + cleanup helper + 이벤트 발화 | 40분 |
| 3 | 신규 테스트 6~8 케이스 | 60분 |
| 4 | 기존 회귀 확인 | 20분 |
| 5 | JavaDoc + CHANGELOG + README (필요 시) | 30분 |

총 예상: **약 2.5~3시간**

---

## 8. 공개 API 변경

### 추가
- `FileEventListener.onUploadFailed(FileMetadata, Throwable)` default method
- `FileEventPublisher.fireUploadFailed(FileMetadata, Throwable)` public method

### 변경
- `FileUploadService.doUpload` 실패 경로 시맨틱 — save 실패 시 storage 정리
- JavaDoc 업데이트

### Breaking
- 없음 (default method, 기존 리스너 구현체 수정 불필요)

### 마이그레이션 노트
- 기존 `try { upload(..., callback) } catch(CALLBACK_FAILED)` 코드는 동작 유지
- 신규 권장 패턴: `FileEventListener` 구현에 `onUploadFailed` 오버라이드 + `FileEventPublisher(List.of(listener))` 등록

---

## 9. Next Steps

1. [ ] `/pdca design callback-quota-rollback` — §2.3 유보 결정 5건 확정
2. [ ] 구현 착수
3. [ ] Gap 분석 → 보고서

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
