package io.github.dornol.filekit.validator;

import java.util.Set;

public interface SafeMediaType {

    String getMediaType();

    Set<String> getExtensions();

}
