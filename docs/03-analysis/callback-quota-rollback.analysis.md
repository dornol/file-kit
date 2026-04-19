# Gap Analysis: callback-quota-rollback

> **Phase**: Check (PDCA)
> **Date**: 2026-04-19
> **Feature**: callback-quota-rollback
> **Design Ref**: [callback-quota-rollback.design.md](../02-design/features/callback-quota-rollback.design.md)

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Match Rate** | **98%** |
| Design items checked | 17 |
| Fully matched | 15 |
| Minor delta (stylistic) | 2 |
| Missing | 0 |
| Build status | ✅ 1207 tests passing, 0 failures |
| Breaking API | 0 |

---

## Verification Details

### §2 유보 결정 5건 (5/5)

| # | 결정 | 구현 증거 | 상태 |
|---|------|---------|:---:|
| 1 | cause type = `Throwable` | `FileEventListener.java:57` — `onUploadFailed(FileMetadata, Throwable)` | ✅ |
| 2 | metadata "in-memory, not persisted" 문서화 | `FileEventListener.java:45-47` | ✅ |
| 3 | `addSuppressed` + WARN for cleanup 실패 | `FileUploadService.java:442-446`, `456-460` | ✅ |
| 4 | 이벤트 `storage.delete` 후, throw 전 | `doUpload` L328-332 (callback), L337-341 (save); U3 InOrder 검증 | ✅ |
| 5 | cause-type 기반, enum 없음 | `Throwable` 단일 파라미터, JavaDoc에 `instanceof`/`getMessageKey` 패턴 | ✅ |

### §3.1 FileEventListener (2/2)

- `default void onUploadFailed(FileMetadata, Throwable)` + `@since 0.1.14` ✅
- JavaDoc 6항목: callback 트리거 / save 트리거 / suppressed / metadata not persisted / don't re-delete / swallowed — 모두 존재 ✅

### §3.2 FileEventPublisher (1/1)

- `fireUploadFailed` → `dispatch` 위임 (`FileEventPublisher.java:55-57`) ✅

### §3.3 FileUploadService.doUpload (3/3 with 1 minor delta)

| 항목 | 상태 |
|------|:---:|
| executeCallback try/catch → fire → throw | ✅ `L326-332` |
| save try/catch → cleanup → fire → throw-as-is | ✅ `L334-341`, U4/U6에서 `assertSame(saveEx, thrown)` 검증 |
| catch 타입 for save | 🟡 **Design은 `Exception`, 구현은 `RuntimeException`** |

### §3.4 JavaDoc (1/1)

- "Failure handling" 섹션이 "Quota note" 대체 (`FileUploadService.java:212-226`) ✅

### §4.1 executeCallback (1/1 with 1 minor delta)

- storage.delete wrap + addSuppressed ✅ `L440-446`
- 🟡 catch 타입: Design은 `Exception`, 구현은 `RuntimeException`

### §5 테스트 매트릭스 (11/11 커버, 10 메서드)

U4와 U6은 의미상 동일 검증(save 실패 → `storage.delete` 호출 + 원 예외 그대로 전파)이라 한 테스트(`saveFailure_callsStorageDeleteAndPropagatesOriginalException`)로 통합. 나머지 U1/U2/U3/U5/U7/U8/U9/U10/U11 모두 전용 @Test 존재.

### CHANGELOG (2/2)

- Added: `onUploadFailed`, `fireUploadFailed` 명시 ✅
- Changed: save orphan 방지 + callback cleanup suppressed + 실패 관찰성 ✅

---

## Minor Deltas (2건, stylistic)

### D1: save catch 타입 narrowing

- **Design** (§3.3): `catch (Exception saveFailure)`
- **구현**: `catch (RuntimeException saveFailure)` (`FileUploadService.java:337`)
- **영향**: Low. `FileMetadataRepository.save` SPI는 checked 예외 선언 없음 → `RuntimeException`이 SPI 계약 전량 커버. 좁힌 건 안전 측 (실수로 `IOException` 등 무관한 체크드 예외 삼키기 방지)

### D2: executeCallback cleanup catch 타입 narrowing

- **Design** (§4.1): `catch (Exception cleanupEx)`
- **구현**: `catch (RuntimeException cleanupEx)` (`FileUploadService.java:442`)
- **영향**: Low. `FileStorage.delete` 시그니처도 checked 예외 선언 없음 — 동일 논리

**결론**: 두 delta 모두 구현 쪽이 더 정확함. Design 텍스트를 `RuntimeException`으로 업데이트하거나, 혹은 장래 SPI에 checked 예외 추가 가능성에 대비해 구현을 `Exception`으로 확장 — 둘 다 허용되나 **구현 유지 권고** (SPI 변경 시 컴파일러가 잡아줌).

---

## 결론

Match Rate 98% — iterate 불필요. **`/pdca report callback-quota-rollback` 진행 가능**.

Design 문서에 D1/D2 관련 사후 주석을 달거나, 구현을 Design 명시대로 확장하는 선택이 있으나, 현 상태로 report 진행 권장.

## Build Verification

```
./gradlew build
BUILD SUCCESSFUL
1207 tests completed, 0 failures
```
