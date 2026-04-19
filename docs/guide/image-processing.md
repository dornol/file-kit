# Image Processing

Standalone image utilities. Not integrated into the upload flow — call directly where needed.

## Operations

| Operation | SPI | Default |
|---|---|---|
| Metadata extraction | `ImageMetadataExtractor` | `ImageIOMetadataExtractor` |
| Resize | `ImageResizer` | `ImageIOResizer` |
| Thumbnail | `ThumbnailGenerator` | `DefaultThumbnailGenerator` |
| Watermark | `ImageWatermarker` | `ImageIOWatermarker` |
| Rotate | `ImageRotator` | `ImageIORotator` |
| Crop | `ImageCropper` | `ImageIOCropper` |
| EXIF strip | `ExifStripper` | `ImageIOExifStripper` |
| Format convert | `ImageFormatConverter` | `ImageIOFormatConverter` |

All defaults are auto-registered by the Spring Boot starter and overridable with your own `@Bean`.

## Metadata extraction

```java
@Autowired ImageMetadataExtractor metadataExtractor;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));
ImageMetadata metadata = metadataExtractor.extract(imageBytes);
// metadata.width(), metadata.height(), metadata.format()
```

## Resize

```java
@Autowired ImageResizer resizer;

byte[] imageBytes = Files.readAllBytes(Path.of("photo.jpg"));

// Thumbnail (fit within 128x128, preserving aspect ratio)
ResizeResult thumbnail = resizer.resize(imageBytes, ResizeOption.thumbnail(128));

// Fit within bounds (aspect ratio preserved)
ResizeResult fitted = resizer.resize(imageBytes, ResizeOption.fit(800, 600));

// Cover target area (crop to fill, aspect ratio preserved)
ResizeResult covered = resizer.resize(imageBytes, ResizeOption.cover(400, 400));

// Exact dimensions (stretches to fit)
ResizeResult exact = resizer.resize(imageBytes, ResizeOption.exact(1920, 1080));

// Custom: format conversion + quality
ResizeOption option = new ResizeOption(800, 600, ScaleMode.FIT, "jpeg", 0.9f);
ResizeResult converted = resizer.resize(imageBytes, option);
```

Width/height must be positive; quality must be 0.0–1.0. Invalid values throw `IllegalArgumentException` at `ResizeOption` construction.

### Scale modes

| Mode | Behavior |
|---|---|
| `FIT` | Scale to fit, preserving aspect ratio. Result may be smaller than target. |
| `COVER` | Scale to cover, preserving aspect ratio. Result cropped to exact target size. |
| `EXACT` | Scale to exact dimensions, ignoring aspect ratio. |

## Thumbnail

Simplified API built on top of `ImageResizer` with `ScaleMode.FIT`:

```java
@Autowired ThumbnailGenerator thumbnailGenerator;

// Default (200px max dimension, 0.8 quality)
ResizeResult thumbnail = thumbnailGenerator.generate(imageBytes, ThumbnailOption.defaults());

// Custom size
ResizeResult small = thumbnailGenerator.generate(imageBytes, ThumbnailOption.ofSize(128));

// Custom size + format + quality
ThumbnailOption option = new ThumbnailOption(256, "jpeg", 0.9f);
ResizeResult custom = thumbnailGenerator.generate(imageBytes, option);
```

## Watermark

Text or image watermarks:

```java
@Autowired ImageWatermarker watermarker;

// Text watermark (center, 50% opacity)
WatermarkOption textOption = WatermarkOption.text("© 2026 ACME", WatermarkPosition.CENTER, 0.5f);
WatermarkResult result = watermarker.apply(imageBytes, textOption);
// result.data() → bytes, result.metadata() → width/height/format

// Image watermark (logo overlay)
byte[] logo = Files.readAllBytes(Path.of("logo.png"));
WatermarkOption logoOption = WatermarkOption.image(logo, WatermarkPosition.BOTTOM_RIGHT, 0.7f);
WatermarkResult logoResult = watermarker.apply(imageBytes, logoOption);

// Tiled watermark (repeated across entire image)
WatermarkOption tiledOption = WatermarkOption.text("DRAFT", WatermarkPosition.TILED, 0.3f);
WatermarkResult tiledResult = watermarker.apply(imageBytes, tiledOption);

// Full control
WatermarkOption custom = new WatermarkOption(
        WatermarkOption.WatermarkType.TEXT, "Confidential", null,
        WatermarkPosition.CENTER, 0.5f, "Serif", 48, "jpeg", 0.9f);
```

### Positions

| Position | Behavior |
|---|---|
| `CENTER` | Centered |
| `TOP_LEFT` / `TOP_RIGHT` / `BOTTOM_LEFT` / `BOTTOM_RIGHT` | Corner with padding |
| `TILED` | Repeated across the entire image |

## Rotate

90°, 180°, or 270° rotation via `RotateAngle` enum:

```java
@Autowired ImageRotator rotator;

byte[] rotated = rotator.rotate(imageBytes, RotateAngle.DEG_90);
```

## Crop

Pixel region with boundary validation (throws `IllegalArgumentException` on out-of-bounds):

```java
@Autowired ImageCropper cropper;

CropRegion region = new CropRegion(100, 100, 400, 300);  // x, y, width, height
byte[] cropped = cropper.crop(imageBytes, region);
```

## EXIF stripping

Re-encode through ImageIO to drop EXIF and other metadata:

```java
@Autowired ExifStripper exifStripper;

// Default quality (0.95)
byte[] stripped = exifStripper.strip(imageBytes);

// Custom quality
byte[] strippedLow = exifStripper.strip(imageBytes, 0.8f);
```

## Format conversion

Convert between formats (PNG → JPEG, etc.) without resizing:

```java
@Autowired ImageFormatConverter converter;

byte[] pngBytes = Files.readAllBytes(Path.of("image.png"));

// Default quality
ConvertResult result = converter.convert(pngBytes, ConvertOption.of("jpeg"));

// Custom quality
ConvertResult hq = converter.convert(pngBytes, ConvertOption.of("jpeg", 0.95f));
```

## Custom implementation

Every SPI above is overridable — register your own `@Bean` (e.g. Thumbnailator, ImageMagick wrapper):

```java
@Bean
public ImageResizer imageResizer() {
    return new ThumbnailatorResizer();
}

@Bean
public ImageWatermarker imageWatermarker() {
    return new MyCustomWatermarker();
}
```

## Related

- [pdf-metadata.md](pdf-metadata.md) — PDF metadata extraction.
- [batch-operations.md](batch-operations.md) — ZIP archive listing.
- [standalone.md](standalone.md) — using these utilities without Spring Boot.
