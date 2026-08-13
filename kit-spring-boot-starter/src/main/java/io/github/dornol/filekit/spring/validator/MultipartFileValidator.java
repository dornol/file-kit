package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * Validates a single {@link MultipartFile} against the {@link ValidMultipartFile} constraint.
 */
public class MultipartFileValidator extends AbstractMultipartFileValidator<MultipartFile> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public MultipartFileValidator(FileValidationHelper helper) {
        this(helper, 0);
    }

    @Autowired
    public MultipartFileValidator(FileValidationHelper helper,
                                  @Value("${file-kit.max-upload-size:0}") long defaultMaxSize) {
        super(defaultMaxSize);
        this.helper = Objects.requireNonNull(helper, "helper");
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
        return helper.isFileSizeExceeded(new MultipartFileSource(value), support.getMaxSize());
    }

    @Override
    public boolean isValidFilename(MultipartFile value) {
        return helper.isValidFilename(new MultipartFileSource(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(MultipartFile value) {
        return helper.validateMediaTypeAndExtension(new MultipartFileSource(value), support.getAllowedMediaTypes());
    }

    @Override
    public @Nullable String validateImageDimensions(MultipartFile value) {
        return helper.validateImageDimensions(new MultipartFileSource(value),
                support.getMinWidth(), support.getMaxWidth(), support.getMinHeight(), support.getMaxHeight());
    }
}
