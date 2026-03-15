package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;

/**
 * Validates a single {@link FileSource} against the {@link ValidFile} constraint.
 */
public class FileSourceValidator extends AbstractFileValidator<FileSource> {

    private final FileValidationHelper helper;

    public FileSourceValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(FileSource value) {
        return false;
    }

    @Override
    public boolean isFileEmpty(FileSource value) {
        return helper.isFileEmpty(value);
    }

    @Override
    public boolean isFileSizeExceeded(FileSource value) {
        return helper.isFileSizeExceeded(value, getMaxSize());
    }

    @Override
    public boolean isValidFilename(FileSource value) {
        return helper.isValidFilename(value);
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(FileSource value) {
        return helper.validateMediaTypeAndExtension(value, getAllowedMediaTypes());
    }
}
