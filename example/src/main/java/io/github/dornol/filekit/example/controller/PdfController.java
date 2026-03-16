package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.pdf.PdfMetadata;
import io.github.dornol.filekit.pdf.PdfMetadataExtractor;
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
@RequestMapping("/pdf")
public class PdfController {

    private final PdfMetadataExtractor metadataExtractor;

    public PdfController(PdfMetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @PostMapping("/metadata")
    public ResponseEntity<Map<String, Object>> metadata(@RequestParam("file") MultipartFile file) throws IOException {
        PdfMetadata meta = metadataExtractor.extract(file.getBytes());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageCount", meta.pageCount());
        result.put("title", meta.title());
        result.put("author", meta.author());
        result.put("creator", meta.creator());
        result.put("creationDate", meta.creationDate());
        return ResponseEntity.ok(result);
    }
}
