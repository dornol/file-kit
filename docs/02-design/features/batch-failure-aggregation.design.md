# Design: Batch Failure Reason Aggregation

> **Plan**: [batch-failure-aggregation.plan.md](../../01-plan/features/batch-failure-aggregation.plan.md)
> **Status**: Draft · 2026-04-19

---

## 1. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 반환 타입 | **`Map<String, Integer>`** | 초기 리뷰 명시. 사용자 친화 |
| 집계 로직 위치 | **각 record에 직접 (3 lines × 3 class)** | util 추출 비용 > 이득 |
| 메서드 이름 | **`failureReasons()`** | 초기 리뷰 명칭 |

---

## 2. 구현

각 record(`BatchUploadResult`, `BatchTransferResult`, `BatchDeleteResult`)에 아래 메서드 추가:

```java
/**
 * Aggregates failure reasons from {@link #failed} into a count by message.
 *
 * <p>Useful when many files fail for the same underlying reason (e.g. a
 * storage outage): the per-file map may contain dozens of identical entries,
 * whereas this returns {@code {"reason" → count}}.</p>
 *
 * @return immutable map of failure reason → count; empty when all succeeded
 * @since 0.1.19
 */
public Map<String, Integer> failureReasons() {
    return failed.values().stream()
            .collect(Collectors.toUnmodifiableMap(
                    reason -> reason,
                    reason -> 1,
                    Integer::sum));
}
```

Import 추가: `java.util.stream.Collectors`.

---

## 3. 테스트 매트릭스 (각 record × 4)

| # | 케이스 | 검증 |
|---|-------|------|
| F1 | 빈 failed → 빈 map | `isEmpty()` |
| F2 | 단일 이유 3건 | `{reason=3}` |
| F3 | 혼합 이유 (2+1+1) | 각 count 정확 |
| F4 | 반환 map immutable | `assertThrows` put |

12 신규 테스트 총계.

---

## 4. 공개 API

### 추가
- `BatchUploadResult.failureReasons()`
- `BatchTransferResult.failureReasons()`
- `BatchDeleteResult.failureReasons()`

### Breaking
- 없음

---

## 5. Next

`/pdca do batch-failure-aggregation`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
