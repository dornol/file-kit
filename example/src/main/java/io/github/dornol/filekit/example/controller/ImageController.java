package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.image.ImageMetadata;
import io.github.dornol.filekit.image.ImageMetadataExtractor;
import io.github.dornol.filekit.image.ImageResizer;
import io.github.dornol.filekit.image.ImageWatermarker;
import io.github.dornol.filekit.image.ResizeOption;
import io.github.dornol.filekit.image.ResizeResult;
import io.github.dornol.filekit.image.ScaleMode;
import io.github.dornol.filekit.image.ThumbnailGenerator;
import io.github.dornol.filekit.image.ThumbnailOption;
import io.github.dornol.filekit.image.WatermarkOption;
import io.github.dornol.filekit.image.WatermarkPosition;
import io.github.dornol.filekit.image.WatermarkResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/image")
public class ImageController {

    private final ImageMetadataExtractor metadataExtractor;
    private final ImageResizer resizer;
    private final ThumbnailGenerator thumbnailGenerator;
    private final ImageWatermarker watermarker;

    public ImageController(ImageMetadataExtractor metadataExtractor,
                           ImageResizer resizer,
                           ThumbnailGenerator thumbnailGenerator,
                           ImageWatermarker watermarker) {
        this.metadataExtractor = metadataExtractor;
        this.resizer = resizer;
        this.thumbnailGenerator = thumbnailGenerator;
        this.watermarker = watermarker;
    }

    @PostMapping("/metadata")
    public ResponseEntity<Map<String, Object>> metadata(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        ImageMetadata meta = metadataExtractor.extract(bytes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("width", meta.width());
        result.put("height", meta.height());
        result.put("format", meta.format());
        result.put("fileSize", bytes.length);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resize")
    public ResponseEntity<byte[]> resize(
            @RequestParam("file") MultipartFile file,
            @RequestParam("width") int width,
            @RequestParam("height") int height,
            @RequestParam(value = "scaleMode", defaultValue = "FIT") ScaleMode scaleMode,
            @RequestParam(value = "quality", defaultValue = "0.85") float quality,
            @RequestParam(value = "outputFormat", required = false) String outputFormat
    ) throws IOException {
        byte[] bytes = file.getBytes();
        ResizeOption option = new ResizeOption(width, height, scaleMode, outputFormat, quality);
        ResizeResult result = resizer.resize(bytes, option);

        return buildImageResponse(result.data(), result.metadata(),
                "resized_" + width + "x" + height + "." + result.metadata().format());
    }

    @PostMapping("/thumbnail")
    public ResponseEntity<byte[]> thumbnail(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "maxDimension", defaultValue = "200") int maxDimension,
            @RequestParam(value = "quality", defaultValue = "0.8") float quality,
            @RequestParam(value = "outputFormat", required = false) String outputFormat
    ) throws IOException {
        byte[] bytes = file.getBytes();
        ThumbnailOption option = new ThumbnailOption(maxDimension, outputFormat, quality);
        ResizeResult result = thumbnailGenerator.generate(bytes, option);

        return buildImageResponse(result.data(), result.metadata(),
                "thumbnail_" + maxDimension + "." + result.metadata().format());
    }

    @PostMapping("/watermark")
    public ResponseEntity<byte[]> watermark(
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text,
            @RequestParam(value = "position", defaultValue = "CENTER") WatermarkPosition position,
            @RequestParam(value = "opacity", defaultValue = "0.5") float opacity,
            @RequestParam(value = "fontSize", defaultValue = "24") int fontSize
    ) throws IOException {
        byte[] bytes = file.getBytes();
        WatermarkOption option = new WatermarkOption(
                WatermarkOption.WatermarkType.TEXT, text, null,
                position, opacity, "SansSerif", fontSize, null, 0.85f);
        WatermarkResult result = watermarker.apply(bytes, option);

        return buildImageResponse(result.data(), result.metadata(),
                "watermarked." + result.metadata().format());
    }

    private ResponseEntity<byte[]> buildImageResponse(byte[] data, ImageMetadata metadata, String filename) {
        String mediaType = switch (metadata.format().toLowerCase()) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Image-Width", String.valueOf(metadata.width()))
                .header("X-Image-Height", String.valueOf(metadata.height()))
                .header("X-Image-Format", metadata.format())
                .body(data);
    }
}
