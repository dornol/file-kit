package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilenameValidatorTest {

    @Test
    void maxFilenameLengthConstant() {
        assertEquals(200, FilenameValidator.MAX_FILENAME_LENGTH);
    }

    @Nested
    class IsSafe {

        @ParameterizedTest
        @ValueSource(strings = {"file.txt", "photo.jpg", "my file.txt", "사진.png",
                "file.backup.txt", "a", "テスト.xlsx"})
        void safeFilenames(String filename) {
            assertTrue(FilenameValidator.isSafe(filename));
        }

        @Test
        void exactlyMaxLength_safe() {
            String name = "a".repeat(196) + ".txt"; // 200 chars
            assertEquals(200, name.length());
            assertTrue(FilenameValidator.isSafe(name));
        }

        @ParameterizedTest
        @NullAndEmptySource
        void nullAndEmpty_notSafe(String filename) {
            assertFalse(FilenameValidator.isSafe(filename));
        }

        @Test
        void blank_notSafe() {
            assertFalse(FilenameValidator.isSafe("   "));
        }

        @Test
        void exceedsMaxLength_notSafe() {
            assertFalse(FilenameValidator.isSafe("a".repeat(201) + ".txt"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"..", "../etc/passwd", "foo/../bar", "path/file.txt",
                "path\\file.txt", "..\\etc\\passwd", "foo..bar"})
        void traversalCharacters_notSafe(String filename) {
            assertFalse(FilenameValidator.isSafe(filename));
        }
    }

    @Nested
    class ContainsTraversalCharacters {

        @ParameterizedTest
        @ValueSource(strings = {"..", "../etc", "foo/../bar", "a..b"})
        void doubleDot_detected(String filename) {
            assertTrue(FilenameValidator.containsTraversalCharacters(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"path/file", "a/b/c"})
        void forwardSlash_detected(String filename) {
            assertTrue(FilenameValidator.containsTraversalCharacters(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"path\\file", "a\\b"})
        void backslash_detected(String filename) {
            assertTrue(FilenameValidator.containsTraversalCharacters(filename));
        }

        @ParameterizedTest
        @ValueSource(strings = {"file.txt", "photo.jpg", "no-traversal", "file.backup.txt"})
        void safeFilenames_notDetected(String filename) {
            assertFalse(FilenameValidator.containsTraversalCharacters(filename));
        }
    }
}
