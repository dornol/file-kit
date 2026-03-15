package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MessageConverter;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileValidator extends AbstractMultipartFileValidator<MultipartFile> {

    private final FileValidationHelper helper;

    public MultipartFileValidator(FileValidationHelper helper, MessageConverter messageConverter) {
        super(messageConverter);
        this.helper = helper;
    }

    @Override
    public boolean isValidationNotRequired(MultipartFile value) {
        return false;
    }

    @Override
    public boolean isValidMediaType(MultipartFile value) {
        return helper.isValidMediaType(new MultipartFileSource(value), getAllowedMediaTypes());
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
    public boolean isValidExtension(MultipartFile value) {
        return helper.isValidExtension(new MultipartFileSource(value), getAllowedMediaTypes());
    }
}
