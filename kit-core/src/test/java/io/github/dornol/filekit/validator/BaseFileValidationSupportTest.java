package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseFileValidationSupportTest {

    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        context = mock(ConstraintValidatorContext.class);
        violationBuilder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
    }

    // ── init ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void init_populatesAllowedMediaTypesFromEnumClass() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        Class<? extends Enum<? extends SafeMediaType>>[] classes = new Class[]{TestMediaType.class};
        support.init(classes, 1024L);

        Set<SafeMediaType> allowed = support.getAllowedMediaTypes();
        assertEquals(3, allowed.size());
        assertTrue(allowed.contains(TestMediaType.JPEG));
        assertTrue(allowed.contains(TestMediaType.PNG));
        assertTrue(allowed.contains(TestMediaType.PDF));
    }

    @Test
    @SuppressWarnings("unchecked")
    void init_setsMaxSize() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        support.init(new Class[]{TestMediaType.class}, 5000L);

        assertEquals(5000L, support.getMaxSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void init_returnsUnmodifiableSet() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        support.init(new Class[]{TestMediaType.class}, 0L);

        assertThrows(UnsupportedOperationException.class,
                () -> support.getAllowedMediaTypes().add(TestMediaType.JPEG));
    }

    @Test
    @SuppressWarnings("unchecked")
    void init_handlesEmptyEnumClasses() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        support.init(new Class[0], 0L);

        assertTrue(support.getAllowedMediaTypes().isEmpty());
    }

    // ── isValid ──────────────────────────────────────────────────────

    @Test
    void isValid_returnsTrue_whenValueIsNull() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertTrue(support.isValid(null, context));
    }

    @Test
    void isValid_returnsTrue_whenValidationNotRequired() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.validationNotRequired = true;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertTrue(support.isValid("anything", context));
    }

    @Test
    void isValid_returnsFalse_whenFileEmpty() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.fileEmpty = true;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void isValid_returnsFalse_whenFileSizeExceeded() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.fileSizeExceeded = true;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void isValid_returnsFalse_whenFilenameInvalid() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.validFilename = false;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void isValid_returnsFalse_whenMediaTypeInvalid() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.mediaTypeError = "file-kit.validation.unsupported-media-type";
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.unsupported-media-type}");
    }

    @Test
    void isValid_returnsFalse_whenExtensionInvalid() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.mediaTypeError = "file-kit.validation.invalid-extension";
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-extension}");
    }

    @Test
    void isValid_returnsTrue_whenAllChecksPassed() {
        StubCallbacks callbacks = new StubCallbacks();
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertTrue(support.isValid("file", context));
        verify(context, never()).disableDefaultConstraintViolation();
    }

    // ── validation order ─────────────────────────────────────────────

    @Test
    void isValid_checksEmptyBeforeSize() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.fileEmpty = true;
        callbacks.fileSizeExceeded = true;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-empty}");
    }

    @Test
    void isValid_checksSizeBeforeFilename() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.fileSizeExceeded = true;
        callbacks.validFilename = false;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.file-too-large}");
    }

    @Test
    void isValid_checksFilenameBeforeMediaType() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.validFilename = false;
        callbacks.mediaTypeError = "file-kit.validation.unsupported-media-type";
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        assertFalse(support.isValid("file", context));
        verify(context).buildConstraintViolationWithTemplate("{file-kit.validation.invalid-filename}");
    }

    @Test
    void isValid_disablesDefaultConstraintOnFailure() {
        StubCallbacks callbacks = new StubCallbacks();
        callbacks.fileEmpty = true;
        BaseFileValidationSupport<String> support = new BaseFileValidationSupport<>(callbacks);

        support.isValid("file", context);

        verify(context).disableDefaultConstraintViolation();
    }

    // ── Stub callbacks ───────────────────────────────────────────────

    private static class StubCallbacks implements FileValidationCallbacks<String> {

        boolean validationNotRequired = false;
        boolean fileEmpty = false;
        boolean fileSizeExceeded = false;
        boolean validFilename = true;
        String mediaTypeError = null; // null = valid

        @Override
        public boolean isValidationNotRequired(String value) {
            return validationNotRequired;
        }

        @Override
        public boolean isFileEmpty(String value) {
            return fileEmpty;
        }

        @Override
        public boolean isFileSizeExceeded(String value) {
            return fileSizeExceeded;
        }

        @Override
        public boolean isValidFilename(String value) {
            return validFilename;
        }

        @Override
        public String validateMediaTypeAndExtension(String value) {
            return mediaTypeError;
        }
    }

}
