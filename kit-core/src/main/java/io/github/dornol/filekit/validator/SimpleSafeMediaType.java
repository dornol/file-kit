package io.github.dornol.filekit.validator;

import java.util.Set;

public class SimpleSafeMediaType implements SafeMediaType {

    private final String mediaType;
    private final Set<String> extensions;

    public SimpleSafeMediaType(String mediaType, Set<String> extensions) {
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
