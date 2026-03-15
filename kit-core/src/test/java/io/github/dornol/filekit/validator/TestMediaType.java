package io.github.dornol.filekit.validator;

import java.util.Set;

/**
 * Test enum implementing {@link SafeMediaType} for use in unit tests.
 */
enum TestMediaType implements SafeMediaType {

    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    PDF("application/pdf", Set.of("pdf"));

    private final String mediaType;
    private final Set<String> extensions;

    TestMediaType(String mediaType, Set<String> extensions) {
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
