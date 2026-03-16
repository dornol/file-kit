package io.github.dornol.filekit.validator;

import io.github.dornol.filekit.domain.FileSource;
import io.github.dornol.filekit.test.ValidatorTestSupport;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileSourceCollectionValidatorTest {

    private FileSourceCollectionValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FileValidationHelper helper = new FileValidationHelper(new StubMediaTypeDetector());
        validator = new FileSourceCollectionValidator(helper);

        ValidFile annotation = mock(ValidFile.class);
        when(annotation.value()).thenReturn(new Class[]{TestMediaType.class});
        when(annotation.maxSize()).thenReturn(10 * 1024L);
        validator.initialize(annotation);

        context = ValidatorTestSupport.mockContext();
    }

    @Test
    void validCollection_passes() {
        List<FileSource> files = List.of(
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[200])
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
    void anyEmptyFile_failsAll() {
        List<FileSource> files = List.of(
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[0])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void anyOversizedFile_failsAll() {
        List<FileSource> files = List.of(
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.png", new byte[20 * 1024])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void anyInvalidFilename_failsAll() {
        List<FileSource> files = List.of(
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("path\\evil.jpg", new byte[100])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void anyUnsupportedMediaType_failsAll() {
        List<FileSource> files = List.of(
                new TestFileSource("a.jpg", new byte[100]),
                new TestFileSource("b.gif", new byte[100])
        );
        assertFalse(validator.isValid(files, context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void nullHelper_throws() {
        assertThrows(NullPointerException.class, () -> new FileSourceCollectionValidator(null));
    }

    @Test
    void isValidationNotRequired_trueForEmptyCollection() {
        assertTrue(validator.isValidationNotRequired(Collections.emptyList()));
    }

    @Test
    void isValidationNotRequired_falseForNonEmptyCollection() {
        List<FileSource> files = List.of(new TestFileSource("a.jpg", new byte[1]));
        assertFalse(validator.isValidationNotRequired(files));
    }

}
