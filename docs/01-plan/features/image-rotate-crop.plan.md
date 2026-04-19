# Plan: Image Rotate / Crop SPI

## Executive Summary

| Item | Detail |
|------|--------|
| Feature | image-rotate-crop |
| Date | 2026-04-19 |
| Status | Draft |
| Scope | kit-core: `ImageRotator` + `ImageCropper` SPI + ImageIO 기본 구현. resize/watermark와 동일 패턴 |
| Related | 초기 리뷰 A4 |

### Value Delivered

| Perspective | Description |
|-------------|-------------|
| **Problem** | `image/` 패키지에 resize/watermark/EXIF-strip/format-convert는 있으나 **rotate/crop 누락**. 사용자가 회전 (모바일 사진 정상화) / 크롭 (썸네일 고정 영역) 원하면 직접 `BufferedImage + AffineTransform` 작성 필요 — 보일러플레이트 과다 |
| **Solution** | `ImageRotator` / `ImageCropper` SPI + `ImageIORotator` / `ImageIOCropper` 기본 구현. 기존 `ImageIOUtils` helper 재사용. Option/Result records |
| **Function/UX Effect** | `resizer.resize(bytes, opt)` 패턴과 정확히 대칭된 rotate/crop API |
| **Core Value** | 이미지 처리 범위 완결. 초기 리뷰 "반쪽 느낌" 해소 — resize/watermark/rotate/crop/thumbnail/format-convert/EXIF/metadata 전부 커버 |

---

## 1. 배경

### 1.1 기존 image/ 패키지 SPI 패턴

```
XxxOption (record)   — 입력 파라미터 + 팩토리 메서드
Xxx (interface)      — SPI, byte[] → Result
ImageIOXxx (class)   — 기본 구현
XxxResult (record)   — byte[] + ImageMetadata
```

예시 (Resize):
- `ResizeOption.fit(w, h)` / `.cover(w, h)` / `.exact(w, h)` / `.thumbnail(size)`
- `ImageResizer.resize(byte[], ResizeOption)` → `ResizeResult(data, metadata)`
- `ImageIOResizer` 기본. `ImageIOUtils.readImage` / `writeImage` helper 재사용

### 1.2 Rotate 구현 난이도

JDK 단독:
```java
BufferedImage rotated = new BufferedImage(h, w, img.getType());  // 90/270은 w,h 스왑
Graphics2D g = rotated.createGraphics();
AffineTransform tx = new AffineTransform();
tx.translate(h / 2.0, w / 2.0);
tx.rotate(Math.toRadians(angle));
tx.translate(-w / 2.0, -h / 2.0);
g.drawImage(img, tx, null);
g.dispose();
```

매번 재작성. 90/180/270 공통 유즈케이스에 필요.

### 1.3 Crop 구현 난이도

JDK 단독:
```java
BufferedImage cropped = img.getSubimage(x, y, w, h);  // 단순
```
단 byte[] 왕복 + boundary 검증 + 포맷 유지 로직은 여전히 필요. Resize와 동일 패턴으로 감싸면 일관.

---

## 2. 범위

### 2.1 In Scope

- [ ] `ImageRotator` SPI + `RotateOption` + `RotateResult`
- [ ] `ImageIORotator` 구현 (90/180/270도만)
- [ ] `ImageCropper` SPI + `CropOption` + `CropResult`
- [ ] `ImageIOCropper` 구현
- [ ] 각 ImageIO 구현 단위 테스트 (각 6-8 케이스)
- [ ] CHANGELOG

### 2.2 Out of Scope

- 임의 각도 회전 (45도 등) — 여백/투명도 처리 복잡, common use case 아님
- EXIF orientation auto-correction — 별도 기능, 복잡도 ↑. 향후 별건 고려
- Smart crop (얼굴 인식 등) — ML 의존, 범위 밖

### 2.3 유보 결정

- **각도 표현**: `int degrees` vs `enum RotateAngle` vs `Rotation` type
- **Crop 경계 초과 처리**: 예외 vs 자동 clip
- **Result record에 `ImageMetadata` 포함 여부**: resize/watermark와 통일 위해 포함

---

## 3. 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-01 | `RotateOption` — 90/180/270 각도 + outputFormat + quality | High |
| FR-02 | `ImageIORotator.rotate`가 90/270에서 width/height swap | High |
| FR-03 | `CropOption` — x/y/width/height + outputFormat + quality + boundary 검증 | High |
| FR-04 | boundary 초과 → `IllegalArgumentException` (명시적 실패) | High |
| FR-05 | 양쪽 모두 `FileStorageException(IMAGE_PROCESSING_FAILED)` wrap (기존 관례) | High |
| FR-06 | JPEG 출력 시 기존 `ImageIOUtils.writeImage`의 RGB 변환 로직 재사용 | High |

---

## 4. 구현 설계

### 4.1 `RotateOption`

```java
public record RotateOption(
        RotateAngle angle,
        @Nullable String outputFormat,
        float quality
) {
    public RotateOption {
        Objects.requireNonNull(angle, "angle");
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0");
        }
    }

    public static RotateOption of(RotateAngle angle) {
        return new RotateOption(angle, null, 0.85f);
    }
}
```

```java
public enum RotateAngle {
    DEGREES_90(90),
    DEGREES_180(180),
    DEGREES_270(270);

    private final int degrees;
    RotateAngle(int degrees) { this.degrees = degrees; }
    public int degrees() { return degrees; }
}
```

### 4.2 `ImageIORotator`

```java
@Override
public RotateResult rotate(byte[] imageBytes, RotateOption option) {
    try {
        BufferedImage source = ImageIOUtils.readImage(imageBytes);
        String outputFormat = ImageIOUtils.resolveOutputFormat(option.outputFormat(), metadataExtractor, imageBytes);

        BufferedImage rotated = applyRotate(source, option.angle());

        byte[] outputBytes = ImageIOUtils.writeImage(rotated, outputFormat, option.quality());
        ImageMetadata resultMeta = new ImageMetadata(rotated.getWidth(), rotated.getHeight(), outputFormat);

        return new RotateResult(outputBytes, resultMeta);
    } catch (FileStorageException e) {
        throw e;
    } catch (IOException e) {
        throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                "Failed to rotate image", e);
    }
}

private BufferedImage applyRotate(BufferedImage source, RotateAngle angle) {
    int w = source.getWidth();
    int h = source.getHeight();
    boolean swap = angle != RotateAngle.DEGREES_180;
    int outW = swap ? h : w;
    int outH = swap ? w : h;

    int imageType = source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType();
    BufferedImage result = new BufferedImage(outW, outH, imageType);
    Graphics2D g = result.createGraphics();
    try {
        AffineTransform tx = new AffineTransform();
        tx.translate(outW / 2.0, outH / 2.0);
        tx.rotate(Math.toRadians(angle.degrees()));
        tx.translate(-w / 2.0, -h / 2.0);
        g.drawImage(source, tx, null);
    } finally {
        g.dispose();
    }
    return result;
}
```

### 4.3 `CropOption`

```java
public record CropOption(
        int x, int y, int width, int height,
        @Nullable String outputFormat,
        float quality
) {
    public CropOption {
        if (x < 0) throw new IllegalArgumentException("x must be non-negative: " + x);
        if (y < 0) throw new IllegalArgumentException("y must be non-negative: " + y);
        if (width <= 0) throw new IllegalArgumentException("width must be positive: " + width);
        if (height <= 0) throw new IllegalArgumentException("height must be positive: " + height);
        if (quality < 0.0f || quality > 1.0f) throw new IllegalArgumentException("quality range");
    }

    public static CropOption of(int x, int y, int w, int h) {
        return new CropOption(x, y, w, h, null, 0.85f);
    }
}
```

### 4.4 `ImageIOCropper`

```java
@Override
public CropResult crop(byte[] imageBytes, CropOption option) {
    try {
        BufferedImage source = ImageIOUtils.readImage(imageBytes);
        if (option.x() + option.width() > source.getWidth()
                || option.y() + option.height() > source.getHeight()) {
            throw new IllegalArgumentException("crop region exceeds image bounds: "
                    + "image=" + source.getWidth() + "x" + source.getHeight()
                    + ", region=(" + option.x() + "," + option.y() + ")+" + option.width() + "x" + option.height());
        }
        String outputFormat = ImageIOUtils.resolveOutputFormat(option.outputFormat(), metadataExtractor, imageBytes);

        BufferedImage cropped = source.getSubimage(option.x(), option.y(), option.width(), option.height());

        byte[] outputBytes = ImageIOUtils.writeImage(cropped, outputFormat, option.quality());
        ImageMetadata resultMeta = new ImageMetadata(cropped.getWidth(), cropped.getHeight(), outputFormat);

        return new CropResult(outputBytes, resultMeta);
    } catch (FileStorageException e) {
        throw e;
    } catch (IOException e) {
        throw new FileStorageException(FileStorageException.IMAGE_PROCESSING_FAILED,
                "Failed to crop image", e);
    }
}
```

---

## 5. 구현 순서

| 단계 | 작업 | 예상 |
|------|------|------|
| 1 | RotateAngle enum + RotateOption + RotateResult + ImageRotator SPI | 20분 |
| 2 | ImageIORotator + 테스트 (6-8 케이스) | 40분 |
| 3 | CropOption + CropResult + ImageCropper SPI | 15분 |
| 4 | ImageIOCropper + 테스트 (6-8 케이스) | 40분 |
| 5 | CHANGELOG + 회귀 | 15분 |

총: **약 2시간 10분**

---

## 6. 공개 API

### 추가
- `image.RotateAngle` (enum: 90/180/270)
- `image.RotateOption` (record)
- `image.RotateResult` (record)
- `image.ImageRotator` (SPI)
- `image.ImageIORotator` (default impl)
- `image.CropOption` (record)
- `image.CropResult` (record)
- `image.ImageCropper` (SPI)
- `image.ImageIOCropper` (default impl)

### Breaking
- 없음

---

# Design

## 7. 유보 결정 확정

| 쟁점 | 결정 | 근거 |
|------|------|------|
| 각도 표현 | **`RotateAngle` enum (90/180/270)** | 타입 안전. 임의 각도는 별건 |
| Crop 경계 초과 | **`IllegalArgumentException`** | 명시적 실패. clip은 사용자 의도 아닐 가능성 |
| Result record에 ImageMetadata | **포함** | resize/watermark와 통일 |

---

## 8. 테스트 매트릭스

### 8.1 `ImageIORotatorTest` (~7)

| # | 케이스 |
|---|-------|
| R1 | 90도 회전 → width/height swap |
| R2 | 180도 회전 → width/height 유지 |
| R3 | 270도 회전 → width/height swap |
| R4 | outputFormat=null → 원본 포맷 유지 |
| R5 | outputFormat="png" 지정 → PNG 출력 |
| R6 | 손상된 이미지 → FileStorageException |
| R7 | RotateOption null quality 검증 |

### 8.2 `ImageIOCropperTest` (~7)

| # | 케이스 |
|---|-------|
| C1 | 중앙 크롭 → 정확한 width/height |
| C2 | 좌상단 (0,0) 크롭 |
| C3 | 경계 초과 → IllegalArgumentException |
| C4 | 음수 x 생성자 검증 |
| C5 | 0 또는 음수 width 검증 |
| C6 | outputFormat 지정 |
| C7 | 손상된 이미지 → FileStorageException |

---

## 9. Next

`/pdca do image-rotate-crop`

| Version | Date | Author |
|---------|------|--------|
| 0.1 | 2026-04-19 | dhkim |
