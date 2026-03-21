package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Validates a {@code MultipartFile[]} against the {@link ValidMultipartFile} constraint.
 * Validation fails if any element in the array fails.
 */
public class MultipartFileArrayValidator extends AbstractMultipartFileValidator<MultipartFile[]> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public MultipartFileArrayValidator(FileValidationHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
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

    @Override
    public @Nullable String validateImageDimensions(MultipartFile[] value) {
        return helper.validateAllImageDimensions(toSources(value),
                getMinWidth(), getMaxWidth(), getMinHeight(), getMaxHeight());
    }

    private static List<FileSource> toSources(MultipartFile[] files) {
        return Arrays.stream(files).<FileSource>map(MultipartFileSource::new).toList();
    }
}
