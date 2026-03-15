package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates a {@code MultipartFile[]} against the {@link ValidMultipartFile} constraint.
 * Validation fails if any element in the array fails.
 */
public class MultipartFileArrayValidator extends AbstractMultipartFileValidator<MultipartFile[]> {

    private final FileValidationHelper helper;

    public MultipartFileArrayValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(MultipartFile[] value) {
        return value.length == 0;
    }

    @Override
    public boolean isFileEmpty(MultipartFile[] value) {
        for (MultipartFile file : value) {
            if (helper.isFileEmpty(new MultipartFileSource(file))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFileSizeExceeded(MultipartFile[] value) {
        for (MultipartFile file : value) {
            if (helper.isFileSizeExceeded(new MultipartFileSource(file), getMaxSize())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidFilename(MultipartFile[] value) {
        for (MultipartFile file : value) {
            if (!helper.isValidFilename(new MultipartFileSource(file))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String validateMediaTypeAndExtension(MultipartFile[] value) {
        for (MultipartFile file : value) {
            String result = helper.validateMediaTypeAndExtension(new MultipartFileSource(file), getAllowedMediaTypes());
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
