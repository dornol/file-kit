# Plan: Upload Pipeline I/O Reduction

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | upload-pipeline-io |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `FileUploadService.doUpload()` 내 tempFile 재읽기 경로 축소 + dedup 조기 판정 |
| Related | `docs/review/2026-04-19-library-review.md` (R2 / A6), 직전 피처 `streaming-checksum-verify` (재사용할 `ChecksumComputation` SPI 제공) |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `FileUploadService.doUpload()`(L246-308)에서 업로드 1건당 tempFile을 **4번 재오픈**(virus scan L254, checksum L257, format L268, encrypt L356). 파일 크기에 비례해 디스크 I/O 증가, 대용량 업로드 지연. 또한 dedup 판정이 checksum 뒤라 duplicate 파일도 full 스캔/다이제스트를 완료해야 함 |
| **Solution** | Ingest pass 1회에 쓰기 + 체크섬(증분 `ChecksumComputation`) + format magic-byte 버퍼링을 동시 수행. 이후 dedup 체크, duplicate면 즉시 반환하여 가상 스캔·암호화·업로드 스킵 |
| **Function/UX Effect** | tempFile 읽기 4회 → 2회(virus + encrypt), 중복 업로드 시 추가 full-file 처리 0, 업로드 throughput 향상 |
| **Core Value** | 업로드 경로를 다운로드 경로와 동일한 "streaming-first" 모델로 통일 (R1 사이클에서 도입한 `ChecksumComputation` SPI 재사용) |

---

## 1. 배경

### 1.1 현황 I/O 흐름

```
 source ──► tempFile (write L251)
              │
              ├─► virus scan  (read L254, via scanForVirus)
              ├─► checksum    (read L257)
              ├─► format      (read L268)
              └─► encrypt     (read L356 → encryptedFile write)
                                        │
                                        └─► upload  (read L289 → storage)
```

- tempFile **4 reads**, encryptedFile **1 read**, source **1 read**
- dedup 판정이 L261에서 발생 → 이미 checksum/virus scan은 완료된 뒤

### 1.2 병목

- 파일 크기 비례 I/O: 100MB 업로드 = tempFile만 400MB 재읽기
- 중복 업로드(dedup hit): 본질적으로 버려질 파일에 virus scan + format 추출 비용 지불
- `ChecksumComputation`(R1 피처에서 도입됨) 이미 증분 갱신 가능 → 재사용 기회 존재

### 1.3 제약

- **VirusScanner SPI**: `scan(InputStream)` 서명. 증분 스캔 불가. 이번 범위 밖
- **FileFormatExtractor SPI**: `extract(InputStream)` 서명. 대개 magic bytes만 사용 → 앞 N 바이트 버퍼링으로 대체 가능
- **FileEncryptor SPI**: `encrypt(InputStream, OutputStream)` 서명. 증분 불가, 별도 pass 유지
- **Dedup 원자성**: R1 피처와 동일 — `findByChecksum` + `save` 원자성 없음 (기존 계약 유지)

---

## 2. 범위

### 2.1 In Scope

- [ ] Ingest 시 `TeeOutputStream` 패턴으로 write + checksum 동시 수행
- [ ] Format 감지용 헤더 버퍼 (`MagicByteBuffer`, 기본 8KB) — ingest 중 누적, tempFile 재읽기 불필요
- [ ] Dedup 체크를 virus scan 전으로 이동 — duplicate hit 시 virus/format/encrypt/upload 스킵
- [ ] Dedup miss 경로에서 virus scan, encrypt, upload는 기존 유지 (각 1 pass)
- [ ] 기존 `FileFormatExtractor` 호출은 "앞 N 바이트 스트림"으로 감싸기 — 전체 파일을 기대하는 구현체 대비 fallback 유지
- [ ] CHANGELOG `[Unreleased]` 엔트리 추가

### 2.2 Out of Scope

- `VirusScanner` SPI 증분화 (별도 피처 후보)
- Encrypt + upload 단일 pass 파이프라이닝 (PipedInputStream 도입 — 스레드 복잡도 증가)
- Dedup 원자성 (분산 락) — 기존 정책 유지
- Source 스트림 비지속성 환경 대응 (resumable upload — A2 별건)

### 2.3 의사결정 유보 (Design에서 확정)

- **Magic byte 버퍼 크기 기본값**: 8KB가 충분한지? (대다수 magic은 수 바이트, Tika는 최대 16KB peek)
- **Format 추출 실패 시 fallback**: 버퍼만으로 감지 실패한 레거시 `FileFormatExtractor` 구현체를 위해 tempFile 재읽기로 fall back할지, 아니면 예외 낼지
- **Dedup hit 시 virus skip 허용 여부**: 기존 저장본이 감염이 아니라는 보장이 필요한지 정책 결정
- **`MagicByteBuffer`를 public API로 노출할지 package-private으로 숨길지**

---

## 3. 요구사항

### 3.1 기능 요구사항

| ID | 요구사항 | 우선순위 | 상태 |
|----|---------|---------|------|
| FR-01 | Ingest 중 단일 pass로 tempFile 쓰기 + 체크섬 증분 갱신 | High | Pending |
| FR-02 | Ingest 중 앞 N 바이트를 버퍼링하여 `FileFormatExtractor`에 전달 | High | Pending |
| FR-03 | Dedup 체크를 virus scan 전으로 이동, duplicate 시 즉시 반환 | High | Pending |
| FR-04 | Dedup hit 경로에서 virus scan / format / encrypt / upload가 실행되지 않음을 테스트로 검증 | High | Pending |
| FR-05 | Dedup miss 경로는 기존 동작 100% 보존 (virus/encrypt/upload 순서·시맨틱 유지) | High | Pending |
| FR-06 | Magic byte 버퍼가 N 바이트 미만(파일 자체가 작음) 케이스에서도 정상 동작 | Medium | Pending |
| FR-07 | `FileFormatExtractor`가 예외를 던지면 upload 실패 (기존 동작 유지) | Medium | Pending |
| FR-08 | tempFile/encryptedFile cleanup 경로가 기존과 동일하게 보장됨 | High | Pending |

### 3.2 비기능 요구사항

| 항목 | 기준 | 측정 방법 |
|------|------|---------|
| I/O 감소 | dedup miss: tempFile read 4회 → 2회 | 코드 리뷰 + 로그 카운터 |
| dedup hit 단축 | virus/encrypt/upload 0회 호출 | 테스트에서 모의 객체 호출 횟수 검증 |
| 하위 호환 | 기존 `FileFormatExtractor`·`VirusScanner`·`FileEncryptor` 구현체 변경 불필요 | `./gradlew build` |
| 회귀 | 기존 1171 테스트 통과 | CI |

---

## 4. 설계 개요 (Design 단계에서 확정)

### 4.1 파이프라인 변경

```
  source ──► TeeOutputStream ──► tempFile (write)
                │      │
                │      └─► MagicByteBuffer (first N bytes cached)
                │
                └─► ChecksumComputation.update(...)  ◄── R1에서 도입, 재사용

  after ingest:
    ├─► checksum = computation.finish()
    ├─► metadataRepository.findByChecksum(checksum)
    │     └─► hit? return existing immediately
    │
    ├─► format = formatExtractor.extract(headerBuffer.asInputStream())
    ├─► scanForVirus(tempFile)
    ├─► encryptFile(tempFile, encryptedFile)
    └─► storage.upload(encryptedFile)
```

### 4.2 신규·확장 컴포넌트 (예상)

| 이름 | 역할 | 공개도 |
|------|------|-------|
| `ChecksumTeeOutputStream` (또는 재사용) | 쓰기+체크섬 동시 처리 | internal (io/) |
| `MagicByteBuffer` | 앞 N 바이트 캐시, `asInputStream()` 제공 | package-private 혹은 public (Design에서 결정) |
| `FileUploadService.doUpload` | 재작성 (ingest pass 합치기, dedup 이동) | 기존 |

세부 시그니처·상태 머신·테스트 매트릭스는 Design 단계에서 확정.

---

## 5. 성공 기준

### 5.1 Definition of Done

- [ ] FR-01~08 전체 구현
- [ ] 신규 단위 테스트 (TeeStream, MagicByteBuffer)
- [ ] `FileUploadServiceTest`에 dedup-hit-skips-{virus,format,encrypt,upload} 시나리오 추가
- [ ] 통합 테스트: 대용량(10MB+) 업로드 시 tempFile 읽기 횟수 측정 (커스텀 FS spy 또는 로그)
- [ ] 기존 테스트 회귀 0
- [ ] CHANGELOG `[Unreleased]` 업데이트

### 5.2 품질 기준

- [ ] `./gradlew build` 성공
- [ ] 공개 API 변경 시 breaking/non-breaking 명시
- [ ] JavaDoc: `doUpload` 흐름 변경 설명

---

## 6. 위험 및 완화

| 위험 | 영향 | 가능성 | 완화 |
|------|------|-------|------|
| magic byte 8KB로 감지 실패하는 포맷 존재 (Tika의 극단 케이스) | Medium | Low | 감지 실패 시 tempFile 재읽기 fallback (1회 추가 read 허용) — Design에서 확정 |
| dedup hit 시 virus scan 스킵이 정책 위반 | High | Medium | 기본값은 "스킵하지 않음" 유지. opt-in 플래그로 제공 고려 (Design) |
| TeeOutputStream에서 checksum update 중 예외 발생 시 tempFile 정합성 깨짐 | Medium | Low | ingest 실패 경로는 finally에서 tempFile delete (기존 cleanup 재사용) |
| `FileFormatExtractor`가 stream 전체를 소비하는 레거시 구현 | Low | Medium | 버퍼 기반 `ByteArrayInputStream`은 끝까지 읽어도 문제 없음. 버퍼 < 파일크기인 경우에 한해 fallback 필요 |
| 호출 순서 변경으로 숨은 의존성 깨짐 (virus → dedup → encrypt를 dedup → virus → encrypt로) | Medium | Low | FR-05 명시 + 회귀 테스트 |

---

## 7. 구현 순서 (예상)

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `MagicByteBuffer` (io/ 신규) + 단위 테스트 | 30분 |
| 2 | Ingest pass 재작성 — Tee write + checksum + header buffer | 40분 |
| 3 | Dedup 체크 위치 이동 + duplicate fast-path | 20분 |
| 4 | Format 추출 경로를 buffer 기반으로 교체 + fallback | 30분 |
| 5 | `FileUploadServiceTest` 케이스 추가 (dedup-hit-skips-*) | 40분 |
| 6 | 통합 테스트: 대용량 업로드 read 횟수 검증 | 40분 |
| 7 | 기존 테스트 회귀 확인 | 20분 |
| 8 | JavaDoc + CHANGELOG | 20분 |

총 예상: **약 4시간**

---

## 8. 공개 API 변경 요약

### 추가 (예상)
- `io.github.dornol.filekit.io.MagicByteBuffer` (공개 여부는 Design에서 결정)

### 변경 (내부 동작)
- `FileUploadService.doUpload()`: I/O 패스 축소. 공개 시그니처 불변.
- 호출 순서 변경: dedup 체크가 virus scan 앞으로 이동.

### Breaking
- 없음 (예상). SPI 변경 없음.

### 마이그레이션 노트
- `FileFormatExtractor` 구현체가 전체 스트림 스캔에 의존한다면, magic byte 버퍼(기본 8KB) 내에서 판정해야 함. 기본 구현(Tika 등)은 영향 없음.
- Dedup hit 시 virus 재스캔 정책이 필요한 사용자는 Design 단계의 결정을 참고.

---

## 9. Next Steps

1. [ ] Design 문서 작성 (`upload-pipeline-io.design.md`) — §2.3 유보 결정 4건 확정
2. [ ] Design 리뷰
3. [ ] 구현 착수 (`/pdca do upload-pipeline-io`)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-19 | Initial draft | dhkim |
