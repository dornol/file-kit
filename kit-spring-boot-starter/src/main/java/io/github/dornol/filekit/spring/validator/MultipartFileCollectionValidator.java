package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Validates a {@code Collection<MultipartFile>} against the {@link ValidMultipartFile} constraint.
 * Validation fails if any element in the collection fails.
 */
public class MultipartFileCollectionValidator extends AbstractMultipartFileValidator<Collection<MultipartFile>> {

    private final FileValidationHelper helper;

    /** @param helper validation helper for file checks */
    public MultipartFileCollectionValidator(FileValidationHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
    }

    @Override
    public boolean isValidationNotRequired(Collection<MultipartFile> value) {
        return value.isEmpty();
    }

    @Override
    public boolean isFileEmpty(Collection<MultipartFile> value) {
        return helper.isAnyFileEmpty(toSources(value));
    }

    @Override
    public boolean isFileSizeExceeded(Collection<MultipartFile> value) {
        return helper.isAnyFileSizeExceeded(toSources(value), support.getMaxSize());
    }

    @Override
    public boolean isValidFilename(Collection<MultipartFile> value) {
        return helper.isAllValidFilenames(toSources(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(Collection<MultipartFile> value) {
        return helper.validateAllMediaTypeAndExtension(toSources(value), support.getAllowedMediaTypes());
    }

    @Override
    public @Nullable String validateImageDimensions(Collection<MultipartFile> value) {
        return helper.validateAllImageDimensions(toSources(value),
                support.getMinWidth(), support.getMaxWidth(), support.getMinHeight(), support.getMaxHeight());
    }

    private static List<FileSource> toSources(Collection<MultipartFile> files) {
        return files.stream().<FileSource>map(MultipartFileSource::new).toList();
    }
}
