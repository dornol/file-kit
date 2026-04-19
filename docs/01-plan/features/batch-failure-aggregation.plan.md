# Plan: Batch Failure Reason Aggregation

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | batch-failure-aggregation |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `BatchUploadResult`, `BatchTransferResult`, `BatchDeleteResult`에 `failureReasons()` 집계 메서드 추가 |
| Related | 초기 리뷰 R6 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | 3 Batch 결과 레코드가 실패 항목을 `Map<String, String> failed` (키 → 개별 메시지)로만 노출. 스토리지 장애 등으로 N건이 동일 이유로 실패하면 호출자가 로그/알림 집계를 매번 직접 수행 |
| **Solution** | 각 record에 `Map<String, Integer> failureReasons()` 메서드 추가 — `failed.values()`를 그룹핑해서 "reason → count" 반환. 기존 `failed` map은 유지 |
| **Function/UX Effect** | 배치 결과 관측 시 1 라인: `result.failureReasons()` → `{"storage unreachable"=47, "invalid filename"=2}` |
| **Core Value** | 배치 작업 실패 원인 파악이 per-file → by-reason으로 스케일. 기존 API 유지 |

---

## 1. 배경

### 1.1 3 record 현황

| 클래스 | 위치 | 동일 구조 |
|--------|------|:---:|
| `BatchUploadResult` | `upload/` | succeeded + failed |
| `BatchTransferResult` | `transfer/` | succeeded + failed |
| `BatchDeleteResult` | `delete/` | succeeded + failed |

전부 `failed: Map<String, String>` — 키(파일명 or 파일 키) → 메시지.

### 1.2 집계 로직 예시

```
failed = {
  "a.jpg" → "storage unreachable",
  "b.jpg" → "storage unreachable",
  "c.jpg" → "invalid filename",
  "d.jpg" → "storage unreachable"
}
→ failureReasons() = {"storage unreachable"=3, "invalid filename"=1}
```

---

## 2. 범위

### 2.1 In Scope

- [ ] 3 record에 `failureReasons()` 메서드 추가 — 반환 `Map<String, Integer>`, 불변 view
- [ ] 집계 로직 공유 여부 결정 (Design)
- [ ] 각 record 테스트에 새 케이스 추가
- [ ] CHANGELOG

### 2.2 Out of Scope

- `failed` map 구조 변경 (기존 per-file 유지)
- 실패 원인 표준화 (enum 등) — 별건
- async adapter 배치 변형의 실패 집계

### 2.3 유보 결정

- **반환 타입**: `Map<String, Integer>` vs `Map<String, Long>` (Stream `Collectors.counting()` 기본은 Long)
- **집계 로직**: 각 record 중복 vs 유틸 클래스 추출
- **메서드 이름**: `failureReasons` vs `failureCountsByReason` vs `failuresByReason`

---

## 3. 요구사항

| ID | 요구사항 |
|----|---------|
| FR-01 | `failureReasons()` 3 record 모두 구현, 동일 시그니처 |
| FR-02 | 반환 map은 불변 (`Map.copyOf` 또는 `Collectors.toUnmodifiableMap`) |
| FR-03 | `failed.isEmpty()`인 경우 빈 map 반환 |
| FR-04 | 동일 reason 문자열은 같은 키로 병합, count 합산 |
| FR-05 | 기존 public API 100% 유지 |

---

## 4. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | 3 record에 `failureReasons()` 추가 | 20분 |
| 2 | 단위 테스트 추가 (각 record 2-3 케이스) | 25분 |
| 3 | CHANGELOG + 회귀 | 15분 |

총: **약 1시간**

---

## 5. 공개 API

### 추가
- `BatchUploadResult.failureReasons()` → `Map<String, Integer>`
- `BatchTransferResult.failureReasons()` → `Map<String, Integer>`
- `BatchDeleteResult.failureReasons()` → `Map<String, Integer>`

### Breaking
- 없음

---

# Design: Batch Failure Reason Aggregation

---

## 6. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 반환 타입 | **`Map<String, Integer>`** | 초기 리뷰에서 명시. Long은 Stream collector 관례, Integer가 사용자 친화 |
| 집계 로직 위치 | **각 record에 직접 구현 (3줄 동일 로직)** | 3 라인 × 3 class. 공유 util 만드는 비용 > 이득. CLAUDE.md "JDK 몇 줄로 충분" |
| 메서드 이름 | **`failureReasons()`** | 초기 리뷰 명칭 채택. 간결 |

## 7. 구현 스케치

```java
public Map<String, Integer> failureReasons() {
    return failed.values().stream()
            .collect(Collectors.toUnmodifiableMap(
                    reason -> reason,
                    reason -> 1,
                    Integer::sum));
}
```

- `toUnmodifiableMap`의 merge function으로 중복 키 병합
- 빈 `failed` → 빈 immutable map

3 record 모두 동일 코드 (import `java.util.stream.Collectors`만 추가).

## 8. 테스트 매트릭스 (각 record)

| # | 케이스 | 검증 |
|---|-------|------|
| F1 | 빈 failed → 빈 map | `result.failureReasons().isEmpty()` |
| F2 | 단일 이유 3건 → `{reason=3}` | count 확인 |
| F3 | 혼합 이유 → 각 count 정확 | 여러 reason |
| F4 | 반환 map은 immutable | `assertThrows(UnsupportedOperationException)` on put |

각 record별 4 케이스 × 3 = **12 신규 테스트**.

---

## 9. Next

`/pdca do batch-failure-aggregation`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
