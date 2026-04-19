# Design: FileValidationHelper Split

> **Plan**: [validation-helper-split.plan.md](../../01-plan/features/validation-helper-split.plan.md)
> **Status**: Draft · 2026-04-19

---

## 1. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| Validator 가시성 | **public final** | 사용자가 facade 없이 특정 validator만 사용 가능. 다른 `io/` 유틸과 일관 |
| facade 생성자 | **기존 `FileValidationHelper(MediaTypeDetector)` 유지** | 추가 생성자는 YAGNI. 테스트/호출자 영향 0 |
| 기존 테스트 마이그레이션 | **기존 그대로 유지 + 신규 테스트 추가** | 중복 일부 허용. facade 경로 회귀 보장 |

---

## 2. API 정의

### 2.1 `MediaTypeValidator`

```java
package io.github.dornol.filekit.validator;

public final class MediaTypeValidator {

    private static final Logger log = LoggerFactory.getLogger(MediaTypeValidator.class);

    private final MediaTypeDetector detector;

    public MediaTypeValidator(MediaTypeDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    /**
     * Validates media type and extension in a single pass.
     *
     * @return {@code null} if valid, or the message key for the failed check
     */
    public @Nullable String validate(FileSource value, Set<SafeMediaType> allowed) {
        // body moved from FileValidationHelper.validateMediaTypeAndExtension
    }

    public @Nullable String validateAll(Iterable<? extends FileSource> files, Set<SafeMediaType> allowed) {
        // body moved from FileValidationHelper.validateAllMediaTypeAndExtension
    }

    private static String getExtension(String filename) { /* unchanged */ }
}
```

### 2.2 `ImageDimensionValidator`

```java
public final class ImageDimensionValidator {

    private static final Logger log = LoggerFactory.getLogger(ImageDimensionValidator.class);

    public ImageDimensionValidator() {}

    /**
     * Validates image dimensions using ImageIO header read.
     */
    public @Nullable String validate(FileSource value,
                                      int minWidth, int maxWidth,
                                      int minHeight, int maxHeight) {
        // body moved from FileValidationHelper.validateImageDimensions
    }

    public @Nullable String validateAll(Iterable<? extends FileSource> files,
                                         int minWidth, int maxWidth,
                                         int minHeight, int maxHeight) {
        // body moved from FileValidationHelper.validateAllImageDimensions
    }
}
```

### 2.3 `FileValidationHelper` 리팩토링 후 구조

```java
public class FileValidationHelper {

    private static final Logger log = LoggerFactory.getLogger(FileValidationHelper.class);

    private final MediaTypeValidator mediaType;
    private final ImageDimensionValidator imageDim = new ImageDimensionValidator();

    public FileValidationHelper(MediaTypeDetector detector) {
        this.mediaType = new MediaTypeValidator(detector);
    }

    // Size/empty/filename — 기존 inline 유지 (짧음, 응집도 높음)
    public boolean isFileEmpty(FileSource value) { /* unchanged */ }
    public boolean isFileSizeExceeded(FileSource value, long maxSize) { /* unchanged */ }
    public boolean isValidFilename(FileSource value) { /* unchanged — delegates to FilenameValidator */ }
    public boolean isAnyFileEmpty(Iterable<? extends FileSource> files) { /* unchanged */ }
    public boolean isAnyFileSizeExceeded(Iterable<? extends FileSource> files, long maxSize) { /* unchanged */ }
    public boolean isAllValidFilenames(Iterable<? extends FileSource> files) { /* unchanged */ }

    // Media type + extension — 위임
    public @Nullable String validateMediaTypeAndExtension(FileSource value, Set<SafeMediaType> allowed) {
        return mediaType.validate(value, allowed);
    }
    public @Nullable String validateAllMediaTypeAndExtension(Iterable<? extends FileSource> files, Set<SafeMediaType> allowed) {
        return mediaType.validateAll(files, allowed);
    }

    // Image dimension — 위임
    public @Nullable String validateImageDimensions(FileSource value, int mW, int xW, int mH, int xH) {
        return imageDim.validate(value, mW, xW, mH, xH);
    }
    public @Nullable String validateAllImageDimensions(Iterable<? extends FileSource> files, int mW, int xW, int mH, int xH) {
        return imageDim.validateAll(files, mW, xW, mH, xH);
    }
}
```

**LOC 변화**: 281L → ~130L (체감 절반)

### 2.4 동작 동등성 계약

- 로그 메시지 포함 모든 side effect 100% 보존
- `ValidationMessageKeys` 참조 불변
- null 입력 시 NPE 타이밍 불변 (`Objects.requireNonNull`)

---

## 3. 테스트 매트릭스

### 3.1 `MediaTypeValidatorTest` (신규, ~10 케이스)

| # | 케이스 |
|---|-------|
| M1 | 정상 — 허용 타입 + 매칭 확장자 → null |
| M2 | 매체 타입 미허용 → `UNSUPPORTED_MEDIA_TYPE` |
| M3 | 원본 파일명 null → `INVALID_EXTENSION` |
| M4 | 확장자 없음 → `INVALID_EXTENSION` |
| M5 | 확장자 대소문자 — 대문자도 매치 |
| M6 | 확장자 미허용 (타입은 OK) → `INVALID_EXTENSION` |
| M7 | detector IOException → IllegalStateException |
| M8 | `validateAll` 첫 실패 반환 |
| M9 | `validateAll` 전부 통과 → null |
| M10 | 생성자 null detector → NPE |

### 3.2 `ImageDimensionValidatorTest` (신규, ~8 케이스)

| # | 케이스 |
|---|-------|
| I1 | 정상 이미지 + 제약 통과 → null |
| I2 | 최소 width 미달 → `IMAGE_WIDTH_TOO_SMALL` |
| I3 | 최대 width 초과 → `IMAGE_WIDTH_TOO_LARGE` |
| I4 | 최소 height 미달 → `IMAGE_HEIGHT_TOO_SMALL` |
| I5 | 최대 height 초과 → `IMAGE_HEIGHT_TOO_LARGE` |
| I6 | 0 제약 → 제한 없음으로 처리 |
| I7 | 비이미지 파일 → `IMAGE_NOT_READABLE` |
| I8 | `validateAll` 첫 실패 반환 |

### 3.3 기존 `FileValidationHelperTest` 회귀

442+L 그대로 — facade 경로가 동일 결과 반환해야 함.

---

## 4. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | `MediaTypeValidator` 추출 + 테스트 M1-M10 | 45분 |
| 2 | `ImageDimensionValidator` 추출 + 테스트 I1-I8 | 40분 |
| 3 | `FileValidationHelper` 위임 리팩토링 | 15분 |
| 4 | 기존 테스트 회귀 확인 | 10분 |
| 5 | CHANGELOG | 10분 |

총: **약 2시간**

---

## 5. 공개 API

### 추가
- `validator.MediaTypeValidator` (public final)
- `validator.ImageDimensionValidator` (public final)

### 변경
- `FileValidationHelper` 내부 — public signature 불변

### Breaking
- 없음

---

## 6. Next

`/pdca do validation-helper-split`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
