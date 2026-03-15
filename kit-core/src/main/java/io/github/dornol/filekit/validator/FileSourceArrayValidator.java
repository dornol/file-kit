package io.github.dornol.filekit.validator;

/**
 * Validates an array of {@link FileSource} against the {@link ValidFile} constraint.
 * Validation fails if any element in the array fails.
 */
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
    public String validateMediaTypeAndExtension(FileSource[] value) {
        for (FileSource file : value) {
            String result = helper.validateMediaTypeAndExtension(file, getAllowedMediaTypes());
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
