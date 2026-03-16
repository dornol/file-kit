package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.test.ValidatorTestSupport;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileSourceArrayValidatorTest {

    private FileSourceArrayValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FileValidationHelper helper = new FileValidationHelper(new StubMediaTypeDetector());
        validator = new FileSourceArrayValidator(helper);

        ValidFile annotation = mock(ValidFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(10 * 1024L);
        validator.initialize(annotation);

        context = ValidatorTestSupport.mockContext();
    }

    @Test
    void validArray_passes() {
        FileSource[] files = {
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[200])
        };
        assertTrue(validator.isValid(files, context));
    }

    @Test
    void emptyArray_passes() {
        assertTrue(validator.isValid(new FileSource[0], context));
    }

    @Test
    void nullArray_passes() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void anyEmptyFile_failsAll() {
        FileSource[] files = {
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[0])
        };
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void anyOversizedFile_failsAll() {
        FileSource[] files = {
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[20 * 1024])
        };
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void anyInvalidFilename_failsAll() {
        FileSource[] files = {
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("../evil.jpg", new byte[100])
        };
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void anyUnsupportedMediaType_failsAll() {
        FileSource[] files = {
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.gif", new byte[100])
        };
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void isValidationNotRequired_trueForEmptyArray() {
        assertTrue(validator.isValidationNotRequired(new FileSource[0]));
    }

    @Test
    void isValidationNotRequired_falseForNonEmptyArray() {
        FileSource[] files = {new TestFileSource("a.jpg", new byte[1])};
        assertFalse(validator.isValidationNotRequired(files));
    }

}
