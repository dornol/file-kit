package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

/**
 * Validates a {@code Collection<MultipartFile>} against the {@link ValidMultipartFile} constraint.
 * Validation fails if any element in the collection fails.
 */
public class MultipartFileCollectionValidator extends AbstractMultipartFileValidator<Collection<MultipartFile>> {

    private final FileValidationHelper helper;

    public MultipartFileCollectionValidator(FileValidationHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(Collection<MultipartFile> value) {
        return value.isEmpty();
    }

    @Override
    public boolean isValidMediaType(Collection<MultipartFile> value) {
        for (MultipartFile file : value) {
            if (!helper.isValidMediaType(new MultipartFileSource(file), getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isFileEmpty(Collection<MultipartFile> value) {
        for (MultipartFile file : value) {
            if (helper.isFileEmpty(new MultipartFileSource(file))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFileSizeExceeded(Collection<MultipartFile> value) {
        for (MultipartFile file : value) {
            if (helper.isFileSizeExceeded(new MultipartFileSource(file), getMaxSize())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidFilename(Collection<MultipartFile> value) {
        for (MultipartFile file : value) {
            if (!helper.isValidFilename(new MultipartFileSource(file))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidExtension(Collection<MultipartFile> value) {
        for (MultipartFile file : value) {
            if (!helper.isValidExtension(new MultipartFileSource(file), getAllowedMediaTypes())) {
                return false;
            }
        }
        return true;
    }
}
