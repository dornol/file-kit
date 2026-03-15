package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.validator.FileValidationHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

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
    public boolean isFileEmpty(Collection<MultipartFile> value) {
        return helper.isAnyFileEmpty(toSources(value));
    }

    @Override
    public boolean isFileSizeExceeded(Collection<MultipartFile> value) {
        return helper.isAnyFileSizeExceeded(toSources(value), getMaxSize());
    }

    @Override
    public boolean isValidFilename(Collection<MultipartFile> value) {
        return helper.isAllValidFilenames(toSources(value));
    }

    @Override
    public @Nullable String validateMediaTypeAndExtension(Collection<MultipartFile> value) {
        return helper.validateAllMediaTypeAndExtension(toSources(value), getAllowedMediaTypes());
    }

    private static List<FileSource> toSources(Collection<MultipartFile> files) {
        return files.stream().map(MultipartFileSource::new).map(FileSource.class::cast).toList();
    }
}
