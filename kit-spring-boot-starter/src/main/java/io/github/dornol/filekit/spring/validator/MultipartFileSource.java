package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.domain.FileSource;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adapter that wraps a Spring {@link MultipartFile} as a {@link FileSource}.
 *
 * <p>This allows the core validation logic (which works with {@code FileSource})
 * to be used with Spring's multipart file handling.</p>
 */
public class MultipartFileSource implements FileSource {

    private final MultipartFile multipartFile;

    public MultipartFileSource(MultipartFile multipartFile) {
        this.multipartFile = multipartFile;
    }

    @Override
    public @Nullable String getOriginalFilename() {
        return multipartFile.getOriginalFilename();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return multipartFile.getInputStream();
    }

    @Override
    public long getSize() {
        return multipartFile.getSize();
    }

    @Override
    public boolean isEmpty() {
        return multipartFile.isEmpty();
    }

}
