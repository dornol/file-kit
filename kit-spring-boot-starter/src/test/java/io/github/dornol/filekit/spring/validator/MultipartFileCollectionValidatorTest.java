package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultipartFileCollectionValidatorTest {

    private MultipartFileCollectionValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MediaTypeDetector detector = new StubMediaTypeDetector();
        FileValidationHelper helper = new FileValidationHelper(detector);
        validator = new MultipartFileCollectionValidator(helper);

        ValidMultipartFile annotation = mock(ValidMultipartFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(10 * 1024L);
        validator.initialize(annotation);

        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    }

    @Test
    void validCollection_passes() throws IOException {
        List<MultipartFile> files = List.of(
                mockMultipartFile("a.jpg", new byte[100]),
                mockMultipartFile("b.png", new byte[200])
        );
        assertTrue(validator.isValid(files, context));
    }

    @Test
    void emptyCollection_passes() {
        assertTrue(validator.isValid(Collections.emptyList(), context));
    }

    @Test
    void nullCollection_passes() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void anyEmptyFile_failsAll() throws IOException {
        MultipartFile empty = mockMultipartFile("b.png", new byte[0]);
        when(empty.isEmpty()).thenReturn(true);

        List<MultipartFile> files = List.of(
                mockMultipartFile("a.jpg", new byte[100]),
                empty
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void anyUnsupportedMediaType_failsAll() throws IOException {
        List<MultipartFile> files = List.of(
                mockMultipartFile("a.jpg", new byte[100]),
                mockMultipartFile("b.gif", new byte[100])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void anyOversizedFile_failsAll() throws IOException {
        List<MultipartFile> files = List.of(
                mockMultipartFile("a.jpg", new byte[100]),
                mockMultipartFile("b.png", new byte[20 * 1024])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    private static MultipartFile mockMultipartFile(String filename, byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(file.getSize()).thenReturn((long) content.length);
        when(file.isEmpty()).thenReturn(content.length == 0);
        return file;
    }

}
