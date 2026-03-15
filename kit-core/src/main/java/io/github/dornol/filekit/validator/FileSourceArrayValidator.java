package io.github.dornol.filekit.validator;

public class FileSourceArrayValidator extends AbstractFileValidator<FileSource[]> {

    private final FileValidationHelper helper;

    public FileSourceArrayValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(FileSource[] value) {
        return value.length == 0;
    }

    @Override
    public boolean isValidMediaType(FileSource[] value) {
        for (FileSource file : value) {
            if (!helper.isValidMediaType(file, getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isFileEmpty(FileSource[] value) {
        for (FileSource file : value) {
            if (helper.isFileEmpty(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFileSizeExceeded(FileSource[] value) {
        for (FileSource file : value) {
            if (helper.isFileSizeExceeded(file, getMaxSize())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidFilename(FileSource[] value) {
        for (FileSource file : value) {
            if (!helper.isValidFilename(file)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidExtension(FileSource[] value) {
        for (FileSource file : value) {
            if (!helper.isValidExtension(file, getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }
}
