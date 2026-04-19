# Completion Report: Image Rotate / Crop

> **Feature**: image-rotate-crop (A4)
> **Project**: file-kit
> **Completion Date**: 2026-04-19
> **Status**: ✅ Completed
> **Build**: `./gradlew build` — 1354 tests passing, 0 failures

---

## Executive Summary

| Section | Detail |
|---------|--------|
| **Feature** | image-rotate-crop (kit-core image 패키지 확장) |
| **Cycle** | 14th PDCA cycle — 초기 라이브러리 리뷰 항목 사실상 **완결** (A9 skip 제외 전체 완료) |
| **Duration** | 2026-04-19 (Plan → Design → Do → Check → Simplify → Report) |
| **Owner** | dhkim |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `image/` 패키지에 resize/watermark/thumbnail/format-convert/EXIF-strip/metadata는 있었으나 **rotate/crop만 누락**. 사용자가 회전(모바일 사진 정상화)/크롭(썸네일 고정 영역) 원하면 `BufferedImage + AffineTransform` 보일러플레이트 직접 작성 |
| **Solution** | `ImageRotator` (90/180/270 via `RotateAngle` enum) + `ImageCropper` (pixel region) SPI + `ImageIORotator`/`ImageIOCropper` 기본 구현. 기존 `ImageIOUtils` 재사용. resize/watermark와 동일 패턴 |
| **Function/UX Effect** | `rotator.rotate(bytes, RotateOption.of(RotateAngle.DEGREES_90))` 한 줄. boundary 초과는 `IllegalArgumentException` 명시적 |
| **Core Value** | 이미지 처리 범위 완결 — resize/watermark/thumbnail/format-convert/EXIF/metadata + **rotate/crop** 전부 커버. 초기 리뷰 "반쪽 느낌" 해소 |

---

## PDCA Cycle Summary

| Phase | Artifact | Outcome |
|-------|----------|---------|
| Plan | `docs/01-plan/features/image-rotate-crop.plan.md` | 요구사항 FR-01~06, 유보 결정 3건 (enum 각도 / 경계 예외 / Result metadata 포함) |
| Design | `docs/02-design/features/image-rotate-crop.design.md` | Plan §7-§8에 통합 — enum/record/IAE/Result metadata 전부 확정 |
| Do | 9 new main + 2 new test | 21 신규 테스트 |
| Check | `docs/03-analysis/image-rotate-crop.analysis.md` | Match Rate 100% (12/12) |
| Simplify | 리뷰 1건 적용 | `ImageIOUtils` Javadoc의 stale "Used by resize/watermark" 목록 → 일반화 문구로 교체 |
| Report | 본 문서 | |

---

## What Was Built

### Rotate

| 파일 | 역할 |
|------|------|
| `image/RotateAngle.java` | enum: DEGREES_90 / DEGREES_180 / DEGREES_270 (type-safe, 임의 각도 제외) |
| `image/RotateOption.java` | record: (angle, outputFormat, quality) + `of(angle)` factory |
| `image/RotateResult.java` | record: (data, metadata) |
| `image/ImageRotator.java` | SPI: `rotate(byte[], RotateOption) → RotateResult` |
| `image/ImageIORotator.java` | 기본 구현: Graphics2D + AffineTransform. 90/270 → width/height swap, 180 → 유지 |

### Crop

| 파일 | 역할 |
|------|------|
| `image/CropOption.java` | record: (x, y, width, height, outputFormat, quality) + `of(x,y,w,h)` factory. 생성자에서 x/y ≥ 0, w/h > 0 검증 |
| `image/CropResult.java` | record: (data, metadata) |
| `image/ImageCropper.java` | SPI: `crop(byte[], CropOption) → CropResult` |
| `image/ImageIOCropper.java` | 기본 구현: `BufferedImage.getSubimage` + 경계 검증 (초과 시 `IllegalArgumentException`) |

### 테스트 (21 신규)

- `ImageIORotatorTest` 10 — 90/180/270 dimension swap, format 유지/변경, null angle, 손상 이미지, enum accessor, 출력 존재
- `ImageIOCropperTest` 11 — 중앙/원점 크롭, 경계 초과(다중 케이스), option 생성자 검증(음수/0/quality 범위), format 변경, 손상 이미지, 출력 존재

### 수정 (Simplify 결과)

- `ImageIOUtils.java` JavaDoc — "Used by ImageIOResizer and ImageIOWatermarker" stale 문구 → "Used by every `ImageIO*` operation in this package"

---

## Metrics

| Item | Value |
|------|-------|
| Match Rate | **100%** (12/12) |
| Tests | **+21** (1333 → 1354) |
| New public classes | 9 |
| Modified classes | 1 (Javadoc only) |
| Breaking API | 0 |
| 공수 (실제) | ~2h |

---

## 14-Cycle Arc 완결 (마일스톤)

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
14 this    image-rotate-crop                100%   +21    ~2h  ← 이번
─────────────────────────────────────────────────────────────────
14 commits · +203 tests · ~31h · 0 breaking · 평균 match 99.4%
```

### 초기 리뷰 완결 상태

| 항목 | 상태 |
|------|:---:|
| R1 다운로드 체크섬 OOM | ✅ |
| R2 업로드 파이프라인 I/O | ✅ |
| R3 Temp-file 수명주기 | ✅ |
| R3.1 DecryptionHelper release 확장 | ✅ |
| R4 콜백 실패 quota hook | ✅ |
| R4.1 save 실패 orphan 수정 | ✅ |
| R5 FileValidationHelper 분리 | ✅ |
| R6 배치 실패 집계 | ✅ |
| A1 ChecksumVerifyingInputStream | ✅ |
| A3 async adapter | ✅ |
| A3+ async Transfer/Delete/Rename 확장 | ✅ |
| **A4 이미지 rotate/crop** | ✅ **← 이번 완료** |
| A5 ChecksumAlgorithm enum | ✅ |
| A6 ChecksumComputation 재사용 (upload) | ✅ |
| A7 Magic byte MIME fallback | ✅ |
| A8 SignedUrl HMAC | ✅ |
| Parallel batch async (post-A3+) | ✅ |
| A9 MetadataRepository cache decorator | ⏭ skip 권장 (레퍼런스 가치 낮음) |

**초기 리뷰의 모든 유효 항목 완료**. A9는 의도적 skip (CLAUDE.md "앱 책임" 선 + 캐시 구현체는 앱마다 달라 레퍼런스 가치 낮음).

---

## Lessons Learned

1. **패턴 안정화 후 N번째 기능 확장은 기계적**: resize → watermark → thumbnail → format-convert → EXIF-strip → rotate → crop. 동일한 4-piece 구조 (Option record / SPI / ImageIO impl / Result record). 이번 사이클 실제 시간 2h — 설계 시간 제외하면 순수 구현은 1h. 패턴 투자가 장기적으로 회수됨.
2. **Simplify에서 JavaDoc 스테일 체크**: 기능 추가 후 기존 파일의 "Used by X, Y" 문구가 금세 stale됨. 간단한 점검이지만 놓치기 쉬움.
3. **유보 결정을 Plan에서 확정 → Design/Do에서 재논의 없음**: 각도 enum vs int, 경계 초과 예외 vs clip, Result metadata 포함 — 3건 모두 Plan에서 결정하고 그대로 구현. cycle time 단축에 기여.
4. **`BufferedImage.getSubimage`의 raster 공유**: copy 아닌 view. `ImageIO.write` 시점에야 실제 바이트 materialize. 메모리 효율적이고 JDK 표준이라 별도 설계 불필요.
5. **Abstract base class 추출은 여전히 YAGNI**: 4개 `ImageIOXxx` 클래스가 모두 `ImageMetadataExtractor` 필드 + 2 생성자 동일 패턴 가지지만, 추출 시 inheritance 도입 비용 > 중복 제거 이득. CLAUDE.md "premature abstraction 금지" 원칙 그대로.

---

## Follow-ups

| 항목 | 성격 | 비고 |
|---|---|---|
| A9 MetadataRepository cache decorator | 레퍼런스 | **Skip 권장**. 캐시 구현은 앱마다 다르고 (Caffeine/Redis/Ehcache) 참고 가치 낮음 |
| 버전 bump 0.1.10 → 0.2.0 | 릴리즈 준비 | `@since 0.1.11` ~ `0.1.24` 사용 중 — 누적된 기능 규모 고려하면 minor bump 타당 |
| `git push` | 배포 | 14 커밋 누적 |
| `@TempDir` 기반 test isolation | 품질 | 여러 cycle simplify에서 지적된 잠재 flakiness. 실제 문제 발생 안 했으나 CI 병렬화 시 대비 |
| Spring Reactive wrapper (kit-spring-boot-starter) | 확장 | async package 안정 후 가능. 별도 사이클 |
| 임의 각도 회전 / smart crop | 확장 | 초기 리뷰 범위 밖, 요청 생기면 |

---

## Migration Notes

**없음**. 모든 추가는 신규 공개 클래스이며 기존 API 시그니처는 불변.

사용 예시:
```java
ImageRotator rotator = new ImageIORotator();
byte[] rotated = rotator.rotate(imageBytes,
        RotateOption.of(RotateAngle.DEGREES_90)).data();

ImageCropper cropper = new ImageIOCropper();
byte[] cropped = cropper.crop(imageBytes,
        CropOption.of(50, 50, 200, 200)).data();
```

---

## Related Documents

- Plan: `docs/01-plan/features/image-rotate-crop.plan.md`
- Design: `docs/02-design/features/image-rotate-crop.design.md`
- Analysis: `docs/03-analysis/image-rotate-crop.analysis.md`
- 초기 리뷰 (trigger): `docs/review/2026-04-19-library-review.md` (A4)

---

## Sign-Off

| Item | Status |
|------|:---:|
| Match Rate ≥ 90% | ✅ (100%) |
| Tests passing | ✅ (1354 / 0 failures) |
| Breaking changes | ✅ None |
| CHANGELOG updated | ✅ |
| Simplify findings applied | ✅ (1 stale Javadoc fix) |

**Status**: Completed. Ready for commit and release planning.
