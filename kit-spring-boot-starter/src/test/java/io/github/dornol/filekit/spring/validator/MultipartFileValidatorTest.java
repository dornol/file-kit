package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.FileValidationHelper;
import io.github.dornol.filekit.validator.MediaTypeDetector;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultipartFileValidatorTest {

    private MultipartFileValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MediaTypeDetector detector = new StubMediaTypeDetector();
        FileValidationHelper helper = new FileValidationHelper(detector);
        validator = new MultipartFileValidator(helper);

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
    void validFile_passes() throws IOException {
        MultipartFile file = mockMultipartFile("photo.jpg", new byte[100]);
        assertTrue(validator.isValid(file, context));
    }

    @Test
    void nullFile_passes() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void emptyFile_fails() throws IOException {
        MultipartFile file = mockMultipartFile("photo.jpg", new byte[0]);
        when(file.isEmpty()).thenReturn(true);

        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void oversizedFile_fails() throws IOException {
        MultipartFile file = mockMultipartFile("photo.jpg", new byte[20 * 1024]);

        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void invalidFilename_fails() throws IOException {
        MultipartFile file = mockMultipartFile("../evil.jpg", new byte[100]);

        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void unsupportedMediaType_fails() throws IOException {
        MultipartFile file = mockMultipartFile("file.gif", new byte[100]);

        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void mismatchedExtension_fails() throws IOException {
        // Detector always returns image/jpeg, but extension is .png
        FileValidationHelper customHelper = new FileValidationHelper((name, is) -> "image/jpeg");
        MultipartFileValidator customValidator = new MultipartFileValidator(customHelper);

        ValidMultipartFile annotation = mock(ValidMultipartFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(0L);
        customValidator.initialize(annotation);

        MultipartFile file = mockMultipartFile("photo.png", new byte[100]);
        assertFalse(customValidator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-extension}");
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
