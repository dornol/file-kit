package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Validates a single {@link FileSource} against the {@link ValidFile} constraint.
 */
public class FileSourceValidator extends AbstractFileValidator<FileSource> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public FileSourceValidator(FileValidationHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
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
        return helper.isFileSizeExceeded(value, support.getMaxSize());
    }

    @Override
    public boolean isValidFilename(FileSource value) {
        return helper.isValidFilename(value);
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(FileSource value) {
        return helper.validateMediaTypeAndExtension(value, support.getAllowedMediaTypes());
    }

    @Override
    public @Nullable String validateImageDimensions(FileSource value) {
        return helper.validateImageDimensions(value, support.getMinWidth(), support.getMaxWidth(), support.getMinHeight(), support.getMaxHeight());
    }
}
