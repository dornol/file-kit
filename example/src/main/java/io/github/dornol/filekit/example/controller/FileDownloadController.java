package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.delete.BatchDeleteResult;
import io.github.dornol.filekit.delete.FileDeleteService;
import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.example.infra.FileMetadataRepositoryAdapter;
import io.github.dornol.filekit.example.config.StorageType;
import io.github.dornol.filekit.spring.download.FileResponseBuilder;
import io.github.dornol.filekit.transfer.FileTransferService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;
    private final FileDeleteService fileDeleteService;
    private final FileTransferService fileTransferService;
    private final FileMetadataRepositoryAdapter metadataRepository;

    public FileDownloadController(FileDownloadService fileDownloadService,
                                  FileDeleteService fileDeleteService,
                                  FileTransferService fileTransferService,
                                  FileMetadataRepositoryAdapter metadataRepository) {
        this.fileDownloadService = fileDownloadService;
        this.fileDeleteService = fileDeleteService;
        this.fileTransferService = fileTransferService;
        this.metadataRepository = metadataRepository;
    }

    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> files = metadataRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("fileKey", m.key());
                    item.put("filename", m.name());
                    item.put("size", m.size());
                    item.put("mimeType", m.format().mimeType());
                    item.put("storageType", m.location().storageType().name());
                    item.put("downloadUrl", "/files/" + m.key() + "/download");
                    return item;
                })
                .toList();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileKey) {
        DownloadResult result = fileDownloadService.download(fileKey);
        return FileResponseBuilder.download(result.metadata())
                .body(new InputStreamResource(result.content()));
    }

    @DeleteMapping("/files/{fileKey}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String fileKey) {
        fileDeleteService.delete(fileKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("fileKey", fileKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/files/{fileKey}/uri")
    public ResponseEntity<String> resolveUri(@PathVariable String fileKey) {
        return ResponseEntity.ok(fileDownloadService.resolveUri(fileKey));
    }

    @GetMapping("/files/{fileKey}/presigned-url")
    public ResponseEntity<Map<String, Object>> presignedUrl(
            @PathVariable String fileKey,
            @RequestParam(value = "expiration", defaultValue = "3600") long expirationSeconds) {
        String url = fileDownloadService.generatePresignedUrl(fileKey, Duration.ofSeconds(expirationSeconds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("expiresInSeconds", expirationSeconds);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/files/{fileKey}/stream")
    public ResponseEntity<Resource> stream(
            @PathVariable String fileKey,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        DownloadResult result = fileDownloadService.download(fileKey);

        if (rangeHeader != null) {
            var byteRange = io.github.dornol.filekit.domain.ByteRange.parse(rangeHeader, result.metadata().size());
            java.io.InputStream content = result.content();
            try {
                content.skipNBytes(byteRange.start());
                java.io.InputStream bounded = new io.github.dornol.filekit.io.BoundedInputStream(content, byteRange.length());
                return FileResponseBuilder.inline(result.metadata())
                        .range(rangeHeader)
                        .body(new InputStreamResource(bounded));
            } catch (java.io.IOException e) {
                try { content.close(); } catch (java.io.IOException ignored) {}
                throw new io.github.dornol.filekit.storage.FileStorageException(
                        io.github.dornol.filekit.storage.FileStorageException.DOWNLOAD_FAILED,
                        "Failed to seek to range start", e);
            }
        }

        return FileResponseBuilder.inline(result.metadata())
                .body(new InputStreamResource(result.content()));
    }

    @DeleteMapping("/files/batch")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody List<String> fileKeys) {
        BatchDeleteResult result = fileDeleteService.deleteAll(fileKeys);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalRequested", result.totalRequested());
        response.put("succeededCount", result.succeeded().size());
        response.put("failedCount", result.failed().size());
        response.put("succeeded", result.succeeded());
        response.put("failed", result.failed());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/files/{fileKey}/copy")
    public ResponseEntity<Map<String, Object>> copy(
            @PathVariable String fileKey,
            @RequestParam(value = "targetStorageType", defaultValue = "LOCAL") StorageType targetStorageType,
            @RequestParam(value = "targetBucket", defaultValue = "uploads") String targetBucket) {
        FileMetadata copied = fileTransferService.copy(fileKey, targetStorageType, targetBucket);
        return ResponseEntity.ok(toMetadataMap(copied));
    }

    @PostMapping("/files/{fileKey}/move")
    public ResponseEntity<Map<String, Object>> move(
            @PathVariable String fileKey,
            @RequestParam(value = "targetStorageType", defaultValue = "LOCAL") StorageType targetStorageType,
            @RequestParam(value = "targetBucket", defaultValue = "uploads") String targetBucket) {
        FileMetadata moved = fileTransferService.move(fileKey, targetStorageType, targetBucket);
        return ResponseEntity.ok(toMetadataMap(moved));
    }

    private static Map<String, Object> toMetadataMap(FileMetadata m) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileKey", m.key());
        result.put("filename", m.name());
        result.put("size", m.size());
        result.put("mimeType", m.format().mimeType());
        result.put("storageType", m.location().storageType().name());
        result.put("bucket", m.location().bucket());
        return result;
    }

}
