package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Validates an array of {@link FileSource} against the {@link ValidFile} constraint.
 * Validation fails if any element in the array fails.
 */
public class FileSourceArrayValidator extends AbstractFileValidator<FileSource[]> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public FileSourceArrayValidator(FileValidationHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
    }

    @Override
    public boolean isValidationNotRequired(FileSource[] value) {
        return value.length == 0;
    }

    @Override
    public boolean isFileEmpty(FileSource[] value) {
        return helper.isAnyFileEmpty(Arrays.asList(value));
    }

    @Override
    public boolean isFileSizeExceeded(FileSource[] value) {
        return helper.isAnyFileSizeExceeded(Arrays.asList(value), getMaxSize());
    }

    @Override
    public boolean isValidFilename(FileSource[] value) {
        return helper.isAllValidFilenames(Arrays.asList(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(FileSource[] value) {
        return helper.validateAllMediaTypeAndExtension(Arrays.asList(value), getAllowedMediaTypes());
    }
}
