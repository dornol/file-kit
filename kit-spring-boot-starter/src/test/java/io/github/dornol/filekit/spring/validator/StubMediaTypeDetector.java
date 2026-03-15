package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.MediaTypeDetector;

import java.io.InputStream;
import java.util.Map;

/**
 * Deterministic {@link MediaTypeDetector} stub that resolves by file extension.
 */
class StubMediaTypeDetector implements MediaTypeDetector {

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf",
            "gif", "image/gif"
    );

    @Override
    public String detect(String filename, InputStream inputStream) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase();
                String type = EXTENSION_MAP.get(ext);
                if (type != null) {
                    return type;
                }
            }
        }
        return "application/octet-stream";
    }

}
