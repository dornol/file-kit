package io.github.dornol.filekit.validator;

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
    public boolean isValidMediaType(FileSource value) {
        return helper.isValidMediaType(value, getAllowedMediaTypes());
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
    public boolean isValidExtension(FileSource value) {
        return helper.isValidExtension(value, getAllowedMediaTypes());
    }
}
