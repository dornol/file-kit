# file-kit 라이브러리 개선 검토 (임시 메모)

- 작성일: 2026-04-19
- 대상 버전: 0.1.10 (commit 3c1bfc5)
- 성격: ad-hoc 리뷰. PDCA Plan 이전 단계의 브레인스토밍. 확정 과제 아님.

---

## 1. 스냅샷

| 항목 | 값 |
|---|---|
| kit-core | 5,300 LOC / 102 Java 파일 |
| kit-spring-boot-starter | 1,362 LOC / 19 Java 파일 |
| 테스트 | 85 파일 (단위 + 통합) |
| 핵심 SPI | `FileStorage`, `FileMetadataRepository`, `FileFormatExtractor`, `ChecksumCalculator`, `FileEncryptor`, 5×이미지 SPI, `PdfMetadataExtractor`, `QuotaPolicy`, `VirusScanner`, `FileEventListener` |
| 주요 서비스 | FileUploadService(379L), FileTransferService(273L), FileDownloadService(201L), FileDeleteService(124L), FileRenameService(91L), FileValidationHelper(281L) |

설계 원칙(CLAUDE.md)과 구현 정합성 양호. SPI 패턴 일관, pure-Java core 유지, 보안 기본값 보수적.

---

## 2. 리팩토링 후보 (영향 순)

### R1. 다운로드 체크섬 검증의 OOM 위험 (HIGH)

- 위치: `kit-core/.../download/FileDownloadService.java:181`
- 문제: `content.readAllBytes()`로 파일 전체를 힙에 적재한 뒤 `ByteArrayInputStream`으로 교체. 대용량 파일에서 실패 또는 GC 압박.
- JavaDoc(129행)은 "extra read pass"라고만 언급.
- 해결안: `io/` 패키지에 `ChecksumVerifyingInputStream` 추가. `DigestInputStream` 래핑 + `close()` 시점에 기댓값 비교, 불일치 시 `FileStorageException(CHECKSUM_MISMATCH)`.
- 부작용: 소비자가 EOF까지 읽지 않으면 검증 미수행 → 문서 명시 필요.

### R2. 업로드 파이프라인 I/O 중복 (HIGH)

- 위치: `kit-core/.../upload/FileUploadService.java:246-308`
- 문제: temp 파일을 **5번** 재오픈.
  1. L251 write (`Files.copy`)
  2. L254 virus scan (`scanForVirus` 내부 newInputStream)
  3. L257 checksum
  4. L268 format 추출
  5. L279 encryptFile 내부 newInputStream
  6. L289 upload 시 newInputStream
- 해결안: 한 pass로 `TeeOutputStream(write) + DigestOutputStream(checksum) + MimeSniffingOutputStream(format magic bytes)` 조합. 이후 encrypt+upload는 한 번만 읽음. 이론상 6 → 2~3 pass.
- 트레이드오프: 코드 복잡도 증가. 대신 성능·I/O·메모리 3축 개선.

### R3. Temp-file 수명주기 중복 (MEDIUM)

- 위치: `FileUploadService.java:246-308`, `FileTransferService.java:231-272`
- 문제: `createTempFile → try { use } finally { deleteIfExists }` 패턴 동일. transfer 쪽은 encryptedFile 이중 cleanup까지 중복.
- 해결안: `io/TempFileBuffer implements AutoCloseable` 추출. `try-with-resources`로 누수 방지 중앙화.

### R4. 콜백 실패 시 quota 롤백 부재 (MEDIUM)

- 위치: `FileUploadService.java:297-302` + `executeCallback(365-376)`
- 문제: callback 예외 시 `storage.delete(metadata)`로 파일은 제거되나, L284에서 이미 증가시킨 quota는 원복되지 않음. 서비스 레벨 JavaDoc(183-189)에 명시돼 있으나 사용자가 간과하기 쉬움.
- 해결안:
  - (A) `QuotaPolicy`에 `onRelease(storageType, bucket, size)` 추가하고 catch 블록에서 호출.
  - (B) 또는 callback을 save 이후로 이동시키고 save 실패 시 storage.delete → quota release 순서로 처리.
- 주의: metadata unique 제약 위반 시에도 동일 문제 존재 → 일관된 해제 경로 필요.

### R5. FileValidationHelper 비대화 (MEDIUM)

- 위치: `kit-core/.../validator/FileValidationHelper.java` (281L)
- 문제: media type 감지 / 확장자 검증 / 이미지 치수 검증이 한 파일에 혼재.
- 해결안: `MediaTypeValidator`, `ExtensionValidator`, `ImageDimensionValidator`로 분리. public 시그니처는 thin facade로 당분간 유지해 호환성 지킴.

### R6. 배치 실패 누적 방식 (LOW)

- 위치: `BatchUploadResult`, `BatchTransferResult`, `BatchDeleteResult`
- 문제: 실패 항목을 per-file 저장. 스토리지 장애로 N건이 같은 원인으로 실패하면 노이즈.
- 해결안: 기존 per-file 보존하되 `Map<String, Integer> failureReasons` 집계 필드 추가.

---

## 3. 추가할 만한 API

| # | 이름 | 근거 | 범위 |
|---|---|---|---|
| A1 | `ChecksumVerifyingInputStream` | R1과 세트. 스트리밍 검증 | ✅ |
| A2 | `ResumableUpload` / 청크 업로드 SPI | 대용량 lifecycle는 in-scope. S3 Multipart 추상화 | ✅ |
| A3 | 비동기 adapter (`AsyncFileUploadService` 등, Virtual Thread 기반) | core 순수성 유지 가능 | ✅ |
| A4 | 이미지 Rotate / Crop SPI | resize/watermark 있는데 rotate/crop만 누락. JDK 단독으로는 보일러플레이트 과다 | ✅ |
| A5 | `ChecksumAlgorithm` enum 파라미터화 | 현재 SHA-256 하드코딩 (`Sha256ChecksumCalculator`) | ✅ |
| A6 | `FileStorage#uploadWithStreamingChecksum` default 메서드 | R2와 연결. 업로드 중 동시 계산 | ✅ |
| A7 | Magic-byte MIME fallback | `DefaultMediaTypeDetector` 보강. Tika 미탑재 환경 | ✅ |
| A8 | `SignedUrlSigner` HMAC 헬퍼 | Local 스토리지용. 인가 자체는 여전히 앱 책임 선 유지 | ⚠️ 경계 |
| A9 | `MetadataRepositoryCacheDecorator` 레퍼런스 | 실제 캐시는 앱 책임. 데코레이터 SPI 예시로 가치 | ⚠️ 레퍼런스 |
| — | PDF merge/split, OCR, 문서변환 | — | ❌ out-of-scope (CLAUDE.md) |

참고: `PdfMetadata`는 이미 `pageCount` 포함 (`PdfMetadata.java:17`). 추가 불필요.

---

## 4. Spring Boot Starter 관찰

- `FileKitAutoConfiguration`: 다수 bean이 `@ConditionalOnMissingBean` + `@ConditionalOnBean` 체인. 누락 시 silently 비활성화 → 사용자 디버깅 어려움. **원인 로그(WARN)** 권장.
- `FileKitProperties.maxPresignedExpiration`: 양수 검증 없음. 0/음수 허용되면 presigned URL이 항상 실패.
- `PdfKitAutoConfiguration`: PDFBox가 `compileOnly`인데 `@ConditionalOnClass` 보강 여부 재확인 필요. 없으면 런타임 `ClassNotFoundException`.

---

## 5. 위험 구간

1. **체크섬 dedup TOCTOU** — `FileUploadService.java:261-265`. `findByChecksum` → `save` 원자성 없음. DB unique 제약 필수. JavaDoc에 경고는 있으나 위험은 상존.
2. **다운로드 OOM** — R1.
3. **VirusScanner 타임아웃 부재** — `FileUploadService.java:254`. 네트워크 기반 스캐너가 느리면 업로드 스레드 블록.
4. **콜백-quota 불일치** — R4.
5. **LocalFileStorage 경로 검증** — `LocalFileStorage.java` normalize + realPath 두 단계. 정확하나 심볼릭 링크가 많은 환경에선 I/O 비용 증가.

---

## 6. 다음 액션 후보 (ROI 순)

1. **R1 + A1** — 스트리밍 체크섬 검증. 단독 PR 가능, 성능·메모리 동시 개선.
2. **R2 + A6** — 업로드 파이프라인 I/O 축소. R1과 패턴 공유.
3. **R4** — 콜백 실패 시 quota 해제. 정합성 이슈.
4. **A3** — 비동기 adapter 모듈. 신규 코드, 기존 영향 없음.
5. **R5** — FileValidationHelper 분리. 단순하나 의미 중간.

---

## 7. 참고

- 본 문서는 작업 착수 전 브레인스토밍. 확정 범위·우선순위는 `/pdca plan {feature}`로 격상할 때 재검토.
- 검증된 주장만 기재. 미검증 가능성 있는 항목은 "재확인 필요" 표기.
