package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates a single {@link MultipartFile} against the {@link ValidMultipartFile} constraint.
 */
public class MultipartFileValidator extends AbstractMultipartFileValidator<MultipartFile> {

    private final FileValidationHelper helper;

    public MultipartFileValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(MultipartFile value) {
        return false;
    }

    @Override
    public boolean isFileEmpty(MultipartFile value) {
        return helper.isFileEmpty(new MultipartFileSource(value));
    }

    @Override
    public boolean isFileSizeExceeded(MultipartFile value) {
        return helper.isFileSizeExceeded(new MultipartFileSource(value), getMaxSize());
    }

    @Override
    public boolean isValidFilename(MultipartFile value) {
        return helper.isValidFilename(new MultipartFileSource(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(MultipartFile value) {
        return helper.validateMediaTypeAndExtension(new MultipartFileSource(value), getAllowedMediaTypes());
    }
}
