# Plan: FileValidationHelper Split

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | validation-helper-split |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `FileValidationHelper` (281L) 분해 — 책임별 validator로 분리, 기존은 facade로 유지 |
| Related | 초기 리뷰 R5 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `FileValidationHelper.java` (281L)가 4개 책임을 한 클래스에 혼재 — media type/extension(40+L), image dimension(40+L), size/empty(짧음), filename(위임). 단일 변경 이유 원칙 위반, 테스트 파일 442+L으로 비대화 |
| **Solution** | `MediaTypeValidator` + `ImageDimensionValidator` 2개 신규 클래스로 추출. `FileValidationHelper`는 facade로 유지 — 기존 public 메서드는 내부 validator 위임 (backward compat 100%) |
| **Function/UX Effect** | 각 validator 단독 사용 가능, 테스트 분리 가능. facade 사용자는 영향 없음 |
| **Core Value** | Single Responsibility 회복 + public API 안정성 보존. 향후 각 validator 독립 진화 가능 |

---

## 1. 배경

### 1.1 현황 구조

```
FileValidationHelper (281L)
├─ Media type + Extension (L43-86, getExtension L270-279)  ─── 49L
├─ File size/empty (L89-109)                                ─── 21L
├─ Filename (L117-133)                                      ─── 17L (FilenameValidator 위임)
├─ Image dimension (L146-209)                               ─── 64L
└─ Batch variants (L213-268)                                ─── 56L (각 single 메서드 위임)
```

### 1.2 호출자

- `FileSourceValidator`, `FileSourceArrayValidator`, `FileSourceCollectionValidator` (kit-core 내부)
- `BaseFileValidationSupport`
- `FileValidationHelperTest` (442+L)
- Spring 쪽: 없음 (kit-spring-boot-starter은 `FileSource` 기반 자체 validator)

### 1.3 핵심 결정

- **Facade 유지**: `FileValidationHelper` public API 불변 → 호출자 수정 0
- **2개 추출**: Media type + Image dimension만 별도 클래스 (짧은 로직은 facade에 남김)
- **기존 테스트**: 그대로 유지 (facade 통해 호출)
- **신규 테스트**: 각 validator 직접 단위 테스트 추가

---

## 2. 범위

### 2.1 In Scope

- [ ] `MediaTypeValidator` 신설 — `validateMediaTypeAndExtension`, `validateAllMediaTypeAndExtension`, private `getExtension`
- [ ] `ImageDimensionValidator` 신설 — `validateImageDimensions`, `validateAllImageDimensions`
- [ ] `FileValidationHelper` 리팩토링 — 두 신규 validator 인스턴스 보유, 기존 메서드는 위임
- [ ] `MediaTypeValidatorTest` + `ImageDimensionValidatorTest` 신설 (일부 케이스 기존 테스트에서 이동 가능)
- [ ] 기존 `FileValidationHelperTest` 회귀 0
- [ ] CHANGELOG

### 2.2 Out of Scope

- `FilenameValidator` 자체 수정 (이미 별도 클래스)
- Size/empty 로직 추출 (각 2-5줄, 추출 비용 > 이득)
- `BaseFileValidationSupport` 직접 접근 경로 추가

### 2.3 유보 결정

- **신규 validator 가시성**: public vs package-private — Design에서 확정
- **facade 생성자 시그니처**: 기존 `FileValidationHelper(MediaTypeDetector)` 유지 vs 신규 validator 주입 허용 추가
- **기존 테스트 마이그레이션 전략**: 그대로 두고 중복 허용 vs 신규 validator로 이동

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | `MediaTypeValidator`/`ImageDimensionValidator` 각 단독 인스턴스화 가능 | High |
| FR-02 | `FileValidationHelper` public 메서드 시그니처 100% 유지 | High |
| FR-03 | 기존 동작 (log message 포함) 보존 | High |
| FR-04 | 기존 테스트 회귀 0 | High |
| FR-05 | 신규 validator 자체 테스트 ≥ 80% 메서드 커버 | Medium |

---

## 4. 설계 개요

```java
// MediaTypeValidator (~60L)
public final class MediaTypeValidator {
    private final MediaTypeDetector detector;
    public MediaTypeValidator(MediaTypeDetector detector) { ... }
    public @Nullable String validate(FileSource value, Set<SafeMediaType> allowed) { ... }
    public @Nullable String validateAll(Iterable<? extends FileSource> files, Set<SafeMediaType> allowed) { ... }
}

// ImageDimensionValidator (~70L)
public final class ImageDimensionValidator {
    public @Nullable String validate(FileSource value, int minW, int maxW, int minH, int maxH) { ... }
    public @Nullable String validateAll(Iterable<? extends FileSource> files, int minW, int maxW, int minH, int maxH) { ... }
}

// FileValidationHelper (reduced to ~130L)
public class FileValidationHelper {
    private final MediaTypeValidator mediaType;
    private final ImageDimensionValidator imageDim = new ImageDimensionValidator();

    public FileValidationHelper(MediaTypeDetector detector) {
        this.mediaType = new MediaTypeValidator(detector);
    }

    // Size/empty/filename methods remain inline (short, cohesive)
    public boolean isFileEmpty(FileSource value) { ... }
    // ...

    // Media type / image dimension methods delegate
    public @Nullable String validateMediaTypeAndExtension(FileSource v, Set<SafeMediaType> a) {
        return mediaType.validate(v, a);
    }
    public @Nullable String validateImageDimensions(FileSource v, int mW, int xW, int mH, int xH) {
        return imageDim.validate(v, mW, xW, mH, xH);
    }
    // etc.
}
```

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `MediaTypeValidator` + 단위 테스트 | 40분 |
| 2 | `ImageDimensionValidator` + 단위 테스트 | 40분 |
| 3 | `FileValidationHelper` 위임 리팩토링 | 20분 |
| 4 | 기존 테스트 회귀 확인 | 10분 |
| 5 | CHANGELOG | 10분 |

총: **약 2시간**

---

## 6. 공개 API 변경

### 추가
- `MediaTypeValidator`, `ImageDimensionValidator` (public final)

### 변경
- `FileValidationHelper` 내부 구조 (공개 시그니처 불변)

### Breaking
- 없음

---

## 7. Next

`/pdca design validation-helper-split`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
