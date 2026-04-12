package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.example.config.AllowedMediaType;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.spring.validator.MultipartFileSource;
import io.github.dornol.filekit.spring.validator.ValidMultipartFile;
import io.github.dornol.filekit.upload.BatchUploadResult;
import io.github.dornol.filekit.upload.FileUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Validated
public class FileUploadController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file")
            @ValidMultipartFile(value = AllowedMediaType.class, maxSize = MAX_FILE_SIZE)
            MultipartFile file,
            @RequestParam(value = "storageType", defaultValue = "LOCAL") StorageType storageType
    ) throws IOException {
        FileMetadata metadata = fileUploadService.upload(
                new MultipartFileSource(file), storageType, "uploads");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("fileKey", metadata.key());
        result.put("filename", metadata.name());
        result.put("size", metadata.size());
        result.put("mimeType", metadata.format().mimeType());
        result.put("storageType", metadata.location().storageType().name());
        result.put("downloadUrl", "/files/" + metadata.key() + "/download");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<Map<String, Object>> uploadMultiple(
            @RequestParam("files")
            @ValidMultipartFile(value = AllowedMediaType.class, maxSize = MAX_FILE_SIZE)
            MultipartFile[] files,
            @RequestParam(value = "storageType", defaultValue = "LOCAL") StorageType storageType
    ) {
        List<MultipartFileSource> sources = new ArrayList<>();
        for (MultipartFile file : files) {
            sources.add(new MultipartFileSource(file));
        }

        BatchUploadResult batchResult = fileUploadService.uploadAll(sources, storageType, "uploads");

        List<Map<String, Object>> uploaded = new ArrayList<>();
        for (FileMetadata metadata : batchResult.succeeded()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileKey", metadata.key());
            item.put("filename", metadata.name());
            item.put("size", metadata.size());
            item.put("storageType", metadata.location().storageType().name());
            item.put("downloadUrl", "/files/" + metadata.key() + "/download");
            uploaded.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRequested", batchResult.totalRequested());
        result.put("succeededCount", batchResult.succeeded().size());
        result.put("failedCount", batchResult.failed().size());
        result.put("files", uploaded);
        if (!batchResult.failed().isEmpty()) {
            result.put("failed", batchResult.failed());
        }
        return ResponseEntity.ok(result);
    }
}
