package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

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
        return helper.isAnyFileEmpty(toSources(value));
    }

    @Override
    public boolean isFileSizeExceeded(MultipartFile[] value) {
        return helper.isAnyFileSizeExceeded(toSources(value), getMaxSize());
    }

    @Override
    public boolean isValidFilename(MultipartFile[] value) {
        return helper.isAllValidFilenames(toSources(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(MultipartFile[] value) {
        return helper.validateAllMediaTypeAndExtension(toSources(value), getAllowedMediaTypes());
    }

    private static List<FileSource> toSources(MultipartFile[] files) {
        return Arrays.stream(files).map(MultipartFileSource::new).map(FileSource.class::cast).toList();
    }
}
