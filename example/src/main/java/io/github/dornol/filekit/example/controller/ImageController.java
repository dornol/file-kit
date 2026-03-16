package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.image.ImageMetadata;
import io.github.dornol.filekit.image.ImageMetadataExtractor;
import io.github.dornol.filekit.image.ImageResizer;
import io.github.dornol.filekit.image.ResizeOption;
import io.github.dornol.filekit.image.ResizeResult;
import io.github.dornol.filekit.image.ScaleMode;
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

    public ImageController(ImageMetadataExtractor metadataExtractor, ImageResizer resizer) {
        this.metadataExtractor = metadataExtractor;
        this.resizer = resizer;
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

        String format = result.metadata().format();
        String mediaType = switch (format.toLowerCase()) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };

        String filename = "resized_" + width + "x" + height + "." + format;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Image-Width", String.valueOf(result.metadata().width()))
                .header("X-Image-Height", String.valueOf(result.metadata().height()))
                .header("X-Image-Format", result.metadata().format())
                .body(result.data());
    }
}
