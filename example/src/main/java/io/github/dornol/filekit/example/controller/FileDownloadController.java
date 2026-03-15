package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.domain.DownloadResult;
import io.github.dornol.filekit.download.FileDownloadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    public FileDownloadController(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }

    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileKey) {
        DownloadResult result = fileDownloadService.download(fileKey);
        String encodedFilename = URLEncoder.encode(result.metadata().name(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(result.metadata().format().mimeType()))
                .contentLength(result.metadata().size())
                .body(new InputStreamResource(result.content()));
    }

    @GetMapping("/files/{fileKey}/uri")
    public ResponseEntity<String> resolveUri(@PathVariable String fileKey) {
        return ResponseEntity.ok(fileDownloadService.resolveUri(fileKey));
    }

}
