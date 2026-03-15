package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import org.springframework.web.multipart.MultipartFile;

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
    public boolean isValidMediaType(MultipartFile[] value) {
        for (MultipartFile file : value) {
            if (!helper.isValidMediaType(new MultipartFileSource(file), getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
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
    public boolean isValidExtension(MultipartFile[] value) {
        for (MultipartFile file : value) {
            if (!helper.isValidExtension(new MultipartFileSource(file), getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }
}
