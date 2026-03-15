package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Validates a {@link Collection} of {@link FileSource} against the {@link ValidFile} constraint.
 * Validation fails if any element in the collection fails.
 */
public class FileSourceCollectionValidator extends AbstractFileValidator<Collection<FileSource>> {

    private final FileValidationHelper helper;

    public FileSourceCollectionValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(Collection<FileSource> value) {
        return value.isEmpty();
    }

    @Override
    public boolean isFileEmpty(Collection<FileSource> value) {
        for (FileSource file : value) {
            if (helper.isFileEmpty(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFileSizeExceeded(Collection<FileSource> value) {
        for (FileSource file : value) {
            if (helper.isFileSizeExceeded(file, getMaxSize())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidFilename(Collection<FileSource> value) {
        for (FileSource file : value) {
            if (!helper.isValidFilename(file)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(Collection<FileSource> value) {
        for (FileSource file : value) {
            String result = helper.validateMediaTypeAndExtension(file, getAllowedMediaTypes());
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
