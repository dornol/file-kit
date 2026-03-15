package io.github.dornol.filekit.example;

import io.github.dornol.filekit.spring.validator.SpringValidFile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Validated
public class FileUploadController {

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file")
            @SpringValidFile(value = AllowedMediaType.class, maxSize = 10 * 1024 * 1024)
            MultipartFile file
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("filename", file.getOriginalFilename());
        result.put("size", file.getSize());
        result.put("contentType", file.getContentType());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<Map<String, Object>> uploadMultiple(
            @RequestParam("files")
            @SpringValidFile(value = AllowedMediaType.class, maxSize = 10 * 1024 * 1024)
            MultipartFile[] files
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("count", files.length);
        return ResponseEntity.ok(result);
    }
}
