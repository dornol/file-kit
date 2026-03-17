package io.github.dornol.filekit.example.controller;

import io.github.dornol.filekit.archive.ArchiveMetadata;
import io.github.dornol.filekit.archive.ArchiveMetadataExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/archive")
public class ArchiveController {

    private final ArchiveMetadataExtractor archiveMetadataExtractor;

    public ArchiveController(ArchiveMetadataExtractor archiveMetadataExtractor) {
        this.archiveMetadataExtractor = archiveMetadataExtractor;
    }

    @PostMapping("/metadata")
    public ResponseEntity<Map<String, Object>> metadata(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        ArchiveMetadata metadata = archiveMetadataExtractor.extract(bytes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entryCount", metadata.entryCount());
        result.put("totalUncompressedSize", metadata.totalUncompressedSize());

        List<Map<String, Object>> entries = metadata.entries().stream()
                .map(entry -> {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("path", entry.path());
                    e.put("compressedSize", entry.compressedSize());
                    e.put("uncompressedSize", entry.uncompressedSize());
                    e.put("lastModified", entry.lastModified());
                    e.put("directory", entry.directory());
                    return e;
                })
                .toList();
        result.put("entries", entries);

        return ResponseEntity.ok(result);
    }
}
