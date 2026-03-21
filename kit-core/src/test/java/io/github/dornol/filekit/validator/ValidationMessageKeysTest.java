package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidationMessageKeysTest {

    @Test
    void allConstants_areNonNull() throws IllegalAccessException {
        for (Field field : ValidationMessageKeys.class.getDeclaredFields()) {
            if (isPublicStaticFinalString(field)) {
                assertNotNull(field.get(null), field.getName() + " must not be null");
            }
        }
    }

    @Test
    void allConstants_startWithPrefix() throws IllegalAccessException {
        for (Field field : ValidationMessageKeys.class.getDeclaredFields()) {
            if (isPublicStaticFinalString(field)) {
                String value = (String) field.get(null);
                assertTrue(value.startsWith("file-kit.validation."),
                        field.getName() + " should start with 'file-kit.validation.' but was: " + value);
            }
        }
    }

    @Test
    void allConstants_areUnique() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field field : ValidationMessageKeys.class.getDeclaredFields()) {
            if (isPublicStaticFinalString(field)) {
                String value = (String) field.get(null);
                assertTrue(values.add(value),
                        "Duplicate constant value: " + value + " in " + field.getName());
            }
        }
    }

    @Test
    void expectedConstants_exist() {
        assertNotNull(ValidationMessageKeys.FILE_EMPTY);
        assertNotNull(ValidationMessageKeys.FILE_TOO_LARGE);
        assertNotNull(ValidationMessageKeys.INVALID_FILENAME);
        assertNotNull(ValidationMessageKeys.UNSUPPORTED_MEDIA_TYPE);
        assertNotNull(ValidationMessageKeys.INVALID_EXTENSION);
        assertNotNull(ValidationMessageKeys.IMAGE_NOT_READABLE);
        assertNotNull(ValidationMessageKeys.IMAGE_WIDTH_TOO_SMALL);
        assertNotNull(ValidationMessageKeys.IMAGE_WIDTH_TOO_LARGE);
        assertNotNull(ValidationMessageKeys.IMAGE_HEIGHT_TOO_SMALL);
        assertNotNull(ValidationMessageKeys.IMAGE_HEIGHT_TOO_LARGE);
    }

    @Test
    void constantValues_matchExpected() {
        assertEquals("file-kit.validation.file-empty", ValidationMessageKeys.FILE_EMPTY);
        assertEquals("file-kit.validation.file-too-large", ValidationMessageKeys.FILE_TOO_LARGE);
        assertEquals("file-kit.validation.invalid-filename", ValidationMessageKeys.INVALID_FILENAME);
        assertEquals("file-kit.validation.unsupported-media-type", ValidationMessageKeys.UNSUPPORTED_MEDIA_TYPE);
        assertEquals("file-kit.validation.invalid-extension", ValidationMessageKeys.INVALID_EXTENSION);
        assertEquals("file-kit.validation.image-not-readable", ValidationMessageKeys.IMAGE_NOT_READABLE);
        assertEquals("file-kit.validation.image-width-too-small", ValidationMessageKeys.IMAGE_WIDTH_TOO_SMALL);
        assertEquals("file-kit.validation.image-width-too-large", ValidationMessageKeys.IMAGE_WIDTH_TOO_LARGE);
        assertEquals("file-kit.validation.image-height-too-small", ValidationMessageKeys.IMAGE_HEIGHT_TOO_SMALL);
        assertEquals("file-kit.validation.image-height-too-large", ValidationMessageKeys.IMAGE_HEIGHT_TOO_LARGE);
    }

    @Test
    void totalConstantCount() throws IllegalAccessException {
        int count = 0;
        for (Field field : ValidationMessageKeys.class.getDeclaredFields()) {
            if (isPublicStaticFinalString(field)) count++;
        }
        assertEquals(10, count, "Expected 10 validation message key constants");
    }

    private static boolean isPublicStaticFinalString(Field field) {
        int mod = field.getModifiers();
        return Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)
                && field.getType() == String.class;
    }
}
