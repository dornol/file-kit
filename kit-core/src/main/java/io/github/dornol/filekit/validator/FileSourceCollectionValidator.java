package io.github.dornol.filekit.validator;

import java.util.Collection;

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
    public boolean isValidMediaType(Collection<FileSource> value) {
        for (FileSource file : value) {
            if (!helper.isValidMediaType(file, getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
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
    public boolean isValidExtension(Collection<FileSource> value) {
        for (FileSource file : value) {
            if (!helper.isValidExtension(file, getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }
}
