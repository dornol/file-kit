package io.github.dornol.filekit.example.config;

import io.github.dornol.filekit.validator.SafeMediaType;

import java.util.Set;

public enum AllowedMediaType implements SafeMediaType {

    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    GIF("image/gif", Set.of("gif")),
    PDF("application/pdf", Set.of("pdf")),
    ;

    private final String mediaType;
    private final Set<String> extensions;

    AllowedMediaType(String mediaType, Set<String> extensions) {
        this.mediaType = mediaType;
        this.extensions = extensions;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public Set<String> getExtensions() {
        return extensions;
    }
}
