package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileSourceValidatorTest {

    private FileSourceValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FileValidationHelper helper = new FileValidationHelper(new StubMediaTypeDetector());
        validator = new FileSourceValidator(helper);

        // simulate annotation initialization
        ValidFile annotation = mock(ValidFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(10 * 1024L);
        validator.initialize(annotation);

        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    }

    @Test
    void validFile_passes() {
        FileSource file = new TestFileSource("photo.jpg", new byte[100]);
        assertTrue(validator.isValid(file, context));
    }

    @Test
    void nullFile_passes() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void emptyFile_fails() {
        FileSource file = new TestFileSource("photo.jpg", new byte[0]);
        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void oversizedFile_fails() {
        FileSource file = new TestFileSource("photo.jpg", new byte[20 * 1024]);
        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void invalidFilename_fails() {
        FileSource file = new TestFileSource("../evil.jpg", new byte[100]);
        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void unsupportedMediaType_fails() {
        FileSource file = new TestFileSource("file.gif", new byte[100]);
        assertFalse(validator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void mismatchedExtension_fails() {
        // Detector returns image/jpeg for .jpg, but only PNG is allowed? No.
        // Let's create a scenario: file detected as image/jpeg but extension is .png
        FileValidationHelper customHelper = new FileValidationHelper((name, is) -> "image/jpeg");
        FileSourceValidator customValidator = new FileSourceValidator(customHelper);

        ValidFile annotation = mock(ValidFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(0L);
        customValidator.initialize(annotation);

        FileSource file = new TestFileSource("photo.png", new byte[100]);
        assertFalse(customValidator.isValid(file, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-extension}");
    }

    @Test
    void isValidationNotRequired_alwaysFalse() {
        FileSource file = new TestFileSource("photo.jpg", new byte[100]);
        assertFalse(validator.isValidationNotRequired(file));
    }

}
