# Completion Report: Configurable Temp Directory

> **Feature**: configurable-temp-directory
> **Project**: file-kit
> **Completion Date**: 2026-04-19
> **Status**: ✅ Completed
> **Build**: `./gradlew build` — 1358 tests passing, 0 failures

---

## Executive Summary

| Section | Detail |
|---------|--------|
| **Feature** | configurable-temp-directory (quality + ops capability) |
| **Cycle** | 15th PDCA cycle |
| **Duration** | 2026-04-19 (Plan → Design → Do → Check → Simplify → Report) |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | 모든 서비스의 temp file이 `java.io.tmpdir`에 강제. 테스트 `ingestIoException_propagates_andCleansTempFile`이 시스템 tmpdir 스캔 + prefix 필터로 delta 측정 — CI 병렬/crashed-run 잔여물에서 flaky 가능. 프로덕션에서 대용량 업로드용 전용 SSD/tmpfs 마운트 지정 불가 |
| **Solution** | `TempFileBuffer.create(Path, String)` 오버로드 + 3개 서비스 Builder에 `tempDirectory(Path)` 옵션 + `DecryptionHelper.decryptToStream(..., Path)` 3-arg 오버로드. 기본값 null → 시스템 tmpdir 유지 (backward compat 100%) |
| **Function/UX Effect** | 테스트 `@TempDir` 완전 격리, `countUploadTempFiles` helper 삭제. 프로덕션 `.tempDirectory(Paths.get("/mnt/ssd-temp"))` 한 줄 |
| **Core Value** | 운영 환경별 temp 배치 제어 (Docker volume, SSD 마운트, disk quota 관리) + 테스트 신뢰성 동시 확보 |

---

## PDCA Cycle Summary

| Phase | Artifact | Outcome |
|-------|----------|---------|
| Plan | `docs/01-plan/features/configurable-temp-directory.plan.md` | FR-01~07, 유보 결정 3건 (디렉토리 존재 검증 / null 시맨틱 / Download 전달 방식) |
| Design | Plan에 통합 (§7-§8) | 모두 확정: 검증 안 함 / system tmpdir fallback / 3-arg 오버로드 |
| Do | 7 main 수정 + 1 test 재작성 + 4 신규 테스트 | |
| Check | Match Rate 100% (10/10) | |
| Simplify | 5 concerns 리뷰 → 전부 "keep as-is" | async adapter cycle precedent 일관 |
| Report | 본 문서 | |

---

## What Was Built / Modified

### 변경된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `io/TempFileBuffer.java` | `create(Path, String)` 오버로드 추가 (`@since 0.1.25`). 기존 `create(String)`는 `create(null, prefix)`로 위임 |
| `io/DecryptionHelper.java` | `decryptToStream(InputStream, FileEncryptor, Path)` 3-arg 오버로드 추가. 2-arg는 null 전달 위임 |
| `upload/FileUploadService.java` | `tempDirectory` 필드 + Builder `tempDirectory(Path)` 메서드. 두 `TempFileBuffer.create` 호출이 디렉토리 전달 |
| `transfer/FileTransferService.java` | 동일 패턴 |
| `download/FileDownloadService.java` | 동일 패턴. `decryptToStream()` 내부 호출이 3-arg 오버로드로 변경 |

### 변경된 테스트

- `FileUploadServiceTest.ingestIoException_propagates_andCleansTempFile` — `@TempDir` 파라미터 주입으로 재작성. 서비스 인스턴스를 테스트 전용으로 빌드 (`tempDirectory(tempDir)`). `countUploadTempFiles` helper 삭제. 시스템 tmpdir 스캔/prefix 필터 로직 완전 제거.
- `TempFileBufferTest` — TD1~TD4 신규 (4 tests): 디렉토리 지정 / null fallback / null prefix NPE / 존재하지 않는 디렉토리 → IOException

### CHANGELOG

`[Unreleased]`의 Added 섹션 상단에 엔트리 추가. 테스트 격리 + 프로덕션 ops 가치 양쪽 명시.

---

## Metrics

| Item | Value |
|------|-------|
| Match Rate | **100%** (10/10) |
| Tests | **+4** (1354 → 1358) |
| Files modified | 5 main + 1 test |
| Files created | 0 (overload만) |
| Breaking API | 0 |
| 공수 실측 | ~2h |

---

## 15-Cycle Arc

```
#  Commit    Feature                         Match  Tests  공수
─────────────────────────────────────────────────────────────────
01 21aa66e  streaming-checksum-verify       100%   +20    ~5h
02 d8dc3ea  upload-pipeline-io              100%   +15    ~4h
03 28aeada  temp-file-buffer                100%   +11    ~1.5h
04 c814500  callback-quota-rollback          98%   +10    ~3h
05 ba2483a  tempbuffer-release              100%   +5     ~1h
06 f5e6ab2  async-adapter                    98%   +16    ~4h
07 dd73a65  async-adapter-expansion         100%   +19    ~2h
08 85b80c2  validation-helper-split         100%   +23    ~2h
09 aa74df0  batch-failure-aggregation       100%   +12    ~45m
10 662d25f  checksum-algorithm-enum         100%   +13    ~45m
11 b5ae379  async-parallel-batch            100%   +9     ~2h
12 f7cdd1e  magic-byte-mime-fallback        100%   +15    ~2h
13 9072719  signed-url-signer               100%   +14    ~1.5h
14 64a13da  image-rotate-crop               100%   +21    ~2h
15 this    configurable-temp-directory      100%   +4     ~2h  ← 이번
─────────────────────────────────────────────────────────────────
15 commits · +207 tests · ~33h · 0 breaking · 평균 match 99.5%
```

---

## Lessons Learned

1. **테스트 위생 개선은 프로덕션 가치와 결합될 때 scope 정당화가 쉬움**: "test 격리" 단독으로는 2h 투자가 과하다고 볼 수 있지만, Docker volume / SSD 마운트 / disk quota 등 운영 시나리오까지 같은 API로 해결되므로 설계 방향 명확.
2. **Optional 파라미터는 오버로드로 해결**: DecryptionHelper의 2-arg/3-arg처럼 기존 시그니처 유지 + 새 오버로드 추가 = deprecation churn 없이 backward compat 100%.
3. **Simplify "no action" 연속 신호**: 최근 3 사이클(cycle 13/14/15)에서 모두 "ship as-is" 또는 trivial 수정만. 패턴 안정화가 성숙 단계에 진입했다는 지표.
4. **`@TempDir` 도입**: JUnit 5 표준 격리 메커니즘. `@org.junit.jupiter.api.io.TempDir Path tempDir` 파라미터 주입으로 per-test 격리 + 자동 정리.

---

## Follow-ups

| 항목 | 성격 |
|---|---|
| 버전 bump 0.1.10 → 0.2.0 | 릴리즈 준비 — `@since 0.1.11 ~ 0.1.25` 누적 |
| git push (15 커밋) | 배포 |
| Spring Reactive wrapper | 확장 (별 사이클) |
| 기존 tests 중 system tmpdir 의존 여부 추가 감사 | 선택 — `countUploadTempFiles` 외 찾아둔 건 없음 |

---

## Migration Notes

**없음** (backward compat 100%). 옵션 미설정 시 기존과 동일.

사용 예시:
```java
// 프로덕션: 대용량 업로드 전용 SSD 마운트
FileUploadService upload = FileUploadService.builder(...)
        .tempDirectory(Paths.get("/mnt/ssd-temp"))
        .build();

// 테스트: 완전 격리
@Test
void foo(@TempDir Path temp) {
    FileUploadService svc = FileUploadService.builder(...)
            .tempDirectory(temp)
            .build();
    // temp는 테스트 종료 후 자동 삭제
}
```

---

## Sign-Off

| Item | Status |
|------|:---:|
| Match Rate ≥ 90% | ✅ (100%) |
| Tests passing | ✅ (1358 / 0 failures) |
| Breaking changes | ✅ None |
| CHANGELOG updated | ✅ |
| Simplify findings applied | ✅ (0 findings required action) |

**Status**: Completed. Ready for commit.
