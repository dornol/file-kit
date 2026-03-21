package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * Validates a {@link Collection} of {@link FileSource} against the {@link ValidFile} constraint.
 * Validation fails if any element in the collection fails.
 */
public class FileSourceCollectionValidator extends AbstractFileValidator<Collection<FileSource>> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public FileSourceCollectionValidator(FileValidationHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
    }

    @Override
    public boolean isValidationNotRequired(Collection<FileSource> value) {
        return value.isEmpty();
    }

    @Override
    public boolean isFileEmpty(Collection<FileSource> value) {
        return helper.isAnyFileEmpty(value);
    }

    @Override
    public boolean isFileSizeExceeded(Collection<FileSource> value) {
        return helper.isAnyFileSizeExceeded(value, support.getMaxSize());
    }

    @Override
    public boolean isValidFilename(Collection<FileSource> value) {
        return helper.isAllValidFilenames(value);
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(Collection<FileSource> value) {
        return helper.validateAllMediaTypeAndExtension(value, support.getAllowedMediaTypes());
    }

    @Override
    public @Nullable String validateImageDimensions(Collection<FileSource> value) {
        return helper.validateAllImageDimensions(value, support.getMinWidth(), support.getMaxWidth(), support.getMinHeight(), support.getMaxHeight());
    }
}
