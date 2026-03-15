package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.download.FileDownloadService;
import io.github.dornol.filekit.example.infra.FileMetadataRepositoryAdapter;
import io.github.dornol.filekit.spring.download.FileResponseBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;
    private final FileMetadataRepositoryAdapter metadataRepository;

    public FileDownloadController(FileDownloadService fileDownloadService,
                                  FileMetadataRepositoryAdapter metadataRepository) {
        this.fileDownloadService = fileDownloadService;
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

    @GetMapping("/files/{fileKey}/uri")
    public ResponseEntity<String> resolveUri(@PathVariable String fileKey) {
        return ResponseEntity.ok(fileDownloadService.resolveUri(fileKey));
    }

}
