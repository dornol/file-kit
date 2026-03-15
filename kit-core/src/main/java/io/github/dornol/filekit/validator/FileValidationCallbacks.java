package io.github.dornol.filekit.validator;

public interface FileValidationCallbacks<T> {

    boolean isValidationNotRequired(T value);

    boolean isValidMediaType(T value);

    boolean isFileEmpty(T value);

    boolean isFileSizeExceeded(T value);

    boolean isValidFilename(T value);

    boolean isValidExtension(T value);

}
