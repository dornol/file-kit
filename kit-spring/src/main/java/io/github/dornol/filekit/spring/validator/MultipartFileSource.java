package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileSource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public class MultipartFileSource implements FileSource {

    private final MultipartFile multipartFile;

    public MultipartFileSource(MultipartFile multipartFile) {
        this.multipartFile = multipartFile;
    }

    @Override
    public String getOriginalFilename() {
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
