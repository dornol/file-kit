# Plan: Streaming Checksum Verification on Download

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | streaming-checksum-verify |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `FileDownloadService` 체크섬 검증 경로 스트리밍화 + `ChecksumCalculator` SPI 확장 |
| Related | `docs/review/2026-04-19-library-review.md` (R1 / A1 / A6) |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `FileDownloadService.verifyChecksum`(L181)이 `readAllBytes()`로 파일 전체를 힙에 적재. 대용량 파일 다운로드 시 OOM 위험 + TTFB 증가 |
| **Solution** | `ChecksumComputation` 증분 SPI 도입 + `ChecksumVerifyingInputStream` 래퍼. read() 중 디지스트 갱신, EOF 시점에 비교·예외 발생 |
| **Function UX Effect** | 메모리 사용량 O(파일) → O(버퍼), 첫 바이트 도달 즉시 스트리밍 시작, 5GB+ 파일 검증 가능 |
| **Core Value** | 파일 무결성 검증을 '옵션적 기능의 함정'에서 '안전한 기본 경로'로 격상 |

---

## 1. 배경

### 1.1 현황

- `FileDownloadService.download()`에서 `checksumCalculator` 주입 시 옵션적 검증 수행
- 현재 구현(L179-195): InputStream → `readAllBytes()` → `ByteArrayInputStream` 재래핑
- JavaDoc(L125-129)이 "extra read pass"라고 언급하지만 메모리 영향은 명시 안 됨

### 1.2 문제

- 파일 크기에 비례해 힙 사용 → 5GB 파일 검증 시 JVM OOM
- 검증 완료 전에는 첫 바이트도 스트림으로 흘려보내지 못함 (TTFB 악화)
- 현 `ChecksumCalculator` SPI는 터미널(byte[] / InputStream 전체 → String) 형태라 중간 상태 없이 증분 계산 불가

### 1.3 의사결정 히스토리

- B안(`maxVerifiableSize` 가드) 대안 검토 → 응급 처방성으로 판단. 사용자가 체크섬 켰을 때 "큰 파일만 안 되는" 비대칭 경험을 남김
- 본 계획(C안): SPI 확장으로 정면 해결. pre-1.0 시점이 SPI 추가에 가장 저렴한 타이밍

---

## 2. 범위

### 2.1 In Scope

- [ ] `ChecksumComputation` 인터페이스 신설 (kit-core/spi)
- [ ] `ChecksumCalculator#newComputation()` default method 추가 (기존 구현체 호환 위한 buffering fallback 포함)
- [ ] `Sha256ChecksumCalculator`에 `MessageDigest` 기반 true-streaming 구현 override
- [ ] `ChecksumVerifyingInputStream` 신설 (kit-core/io)
- [ ] `FileDownloadService.verifyChecksum()` 교체 — `readAllBytes` 제거
- [ ] 테스트: 정상 / 중간 불일치 / EOF 불일치 / early close / decrypt 합성 / custom calculator fallback / mark-reset 비활성
- [ ] JavaDoc 업데이트 — 소비자가 EOF까지 읽어야 검증 완료됨을 명시

### 2.2 Out of Scope

- 업로드 경로 체크섬 스트리밍화 (R2와 함께 별도 피처)
- `ChecksumAlgorithm` enum 파라미터화 (A5 — 별도 피처)
- Range 다운로드 시 체크섬 검증 활성화 (본 피처는 기존처럼 전체 다운로드 경로에만 적용)
- 엄격 모드 (`strictChecksumOnClose`) — 요청 기반으로 추후 추가
- `DigestInputStream` 직접 사용 (JDK 클래스 재래핑은 SPI 확장 이점 상실)

---

## 3. 요구사항

### 3.1 기능 요구사항

| ID | 요구사항 | 우선순위 | 상태 |
|----|---------|---------|------|
| FR-01 | 다운로드 스트림 read() 중 디지스트가 증분 갱신되어야 한다 | High | Pending |
| FR-02 | EOF 도달 시 디지스트 비교, 불일치 시 `FileStorageException(CHECKSUM_MISMATCH)` | High | Pending |
| FR-03 | 소비자가 EOF 전 `close()` 시 검증 스킵 + WARN 로그 | High | Pending |
| FR-04 | 기존 `ChecksumCalculator` 커스텀 구현체는 기본 메서드로 동작 (buffering fallback) | High | Pending |
| FR-05 | `Sha256ChecksumCalculator`는 `MessageDigest`로 true streaming 제공 | High | Pending |
| FR-06 | decrypt → verify 순서에서 decrypt 예외가 verify보다 우선 전파 | Medium | Pending |
| FR-07 | `markSupported() = false`, `mark()/reset()` 호출 시 일관된 동작 | Medium | Pending |
| FR-08 | `close()` 시 검증 실패 여부와 무관하게 underlying stream도 닫힘 | High | Pending |

### 3.2 비기능 요구사항

| 항목 | 기준 | 측정 방법 |
|------|------|---------|
| 메모리 | 파일 크기와 무관하게 O(버퍼) 사용 | 10GB 가짜 스트림으로 heap 측정 |
| 성능 | 기존 대비 TTFB 개선 (streaming 시작 즉시) | 통합 테스트에서 첫 read()까지 경과 시간 비교 |
| 하위 호환 | 기존 `ChecksumCalculator` 구현체 컴파일 에러 0 | `./gradlew build` |

---

## 4. 설계 개요

### 4.1 SPI 변경

```java
// spi/ChecksumCalculator.java (기존에 추가)
default ChecksumComputation newComputation() {
    return new BufferingComputation(this);
}

// spi/ChecksumComputation.java (신규)
public interface ChecksumComputation {
    void update(byte[] buf, int off, int len);
    String finish();
}

// spi/Sha256ChecksumCalculator.java (override)
@Override public ChecksumComputation newComputation() {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return new ChecksumComputation() { /* MessageDigest wrap */ };
}
```

### 4.2 검증 래퍼

- 위치: `io/ChecksumVerifyingInputStream.java`
- `extends FilterInputStream`
- `read()` / `read(byte[], int, int)` override → 증분 update + EOF 판정
- `close()` → underlying close 보장 (try-finally)
- `markSupported() = false`

### 4.3 서비스 통합

```java
// download/FileDownloadService.java
private InputStream verifyChecksum(InputStream content, FileMetadata metadata) {
    return new ChecksumVerifyingInputStream(
        content, checksumCalculator.newComputation(),
        metadata.checksum(), metadata.key()
    );
}
```

세부 클래스 설계·불변식·테스트 매트릭스는 Design 단계에서 확정.

---

## 5. 성공 기준

### 5.1 Definition of Done

- [ ] 전체 FR-01~08 구현
- [ ] 신규 단위 테스트 통과 (커버리지 신규 클래스 ≥ 90%)
- [ ] 기존 `FileDownloadServiceTest` 회귀 0
- [ ] `UploadDownloadIntegrationTest`에 1GB급 가짜 스트림 검증 시나리오 추가
- [ ] JavaDoc 업데이트 (download, newComputation, ChecksumVerifyingInputStream)
- [ ] CHANGELOG.md 엔트리 추가

### 5.2 품질 기준

- [ ] `./gradlew build` 성공
- [ ] 테스트 추가 이후 전체 test suite 녹색
- [ ] 공개 API 변경 사항을 CHANGELOG에 breaking/non-breaking 명시

---

## 6. 위험 및 완화

| 위험 | 영향 | 가능성 | 완화 |
|------|------|-------|------|
| `FilterInputStream.skip()` 기본 구현이 read 기반이 아닐 경우 디지스트 누락 | Medium | Low | skip을 명시적으로 override하여 read 기반 강제 또는 `UnsupportedOperationException` |
| Range 다운로드 경로가 checksumCalculator를 통과할 수 있음 | High | Low | 코드 재확인 후 해당 경로에서는 wrapping 비활성. Design 단계에서 확정 |
| 커스텀 `ChecksumCalculator` 구현체가 스레드 안전성 가정에 의존 | Medium | Low | `BufferingComputation`은 stateless, 새 인스턴스 생성. JavaDoc에 "not thread-safe per instance" 명시 |
| 예외 타이밍 변화(리턴 후 read 중 예외)로 호출부 try 범위 문제 | Low | Medium | 마이그레이션 노트 추가, JavaDoc 강조 |
| `close()`에서 검증 예외 발생 시 underlying close 누락 | High | Low | try-finally 엄격 적용 + 누락 케이스 전용 테스트 |

---

## 7. 구현 순서

| 단계 | 대상 | 비고 |
|------|------|------|
| 1 | `ChecksumComputation` 인터페이스 + `BufferingComputation` | 가장 단순, 독립 |
| 2 | `ChecksumCalculator#newComputation()` default 추가 | API surface 확장 |
| 3 | `Sha256ChecksumCalculator` override | true streaming 경로 |
| 4 | `ChecksumVerifyingInputStream` + 단위 테스트 | 핵심 로직 |
| 5 | `FileDownloadService.verifyChecksum` 교체 | 서비스 통합 |
| 6 | 통합 테스트 (대용량, decrypt 합성) | 엔드투엔드 검증 |
| 7 | JavaDoc + CHANGELOG | 릴리즈 준비 |

---

## 8. 공개 API 변경 요약

### 추가
- `io.github.dornol.filekit.spi.ChecksumComputation` (신규 인터페이스)
- `ChecksumCalculator#newComputation()` (default method, 기본 구현 제공)
- `io.github.dornol.filekit.io.ChecksumVerifyingInputStream` (신규 public 클래스 — 재사용 가능성 고려)

### 변경
- `FileDownloadService.download()` 반환 `InputStream`의 예외 발생 시점이 "메서드 리턴 이전"에서 "스트림 소비 중"으로 이동. 공개 시그니처는 불변.

### Breaking
- 없음 (SPI는 default method 제공, 서비스 시그니처 유지)

### 마이그레이션 노트
- 커스텀 `ChecksumCalculator`를 구현한 사용자는 성능 위해 `newComputation()` override 권장 (기본 구현은 읽기 후 버퍼링)
- `download()` 호출부의 `try-catch`는 스트림 소비 구간까지 감싸야 `CHECKSUM_MISMATCH`를 잡을 수 있음

---

## 9. Next Steps

1. [ ] Design 문서 작성 (`streaming-checksum-verify.design.md`) — 클래스 다이어그램, 테스트 매트릭스, Range 경로 최종 결정
2. [ ] Design 리뷰
3. [ ] 구현 착수 (`/pdca do streaming-checksum-verify`)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
