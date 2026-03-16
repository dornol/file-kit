package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationHelperTest {

    private FileValidationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new FileValidationHelper(new StubMediaTypeDetector());
    }

    // ── Constructor ────────────────────────────────────────────────────

    @Test
    void nullDetector_throws() {
        assertThrows(NullPointerException.class, () -> new FileValidationHelper(null));
    }

    // ── isFileEmpty ──────────────────────────────────────────────────

    @Nested
    class IsFileEmpty {

        @Test
        void returnsTrueForEmptyFile() {
            TestFileSource file = new TestFileSource("empty.txt", new byte[0]);
            assertTrue(helper.isFileEmpty(file));
        }

        @Test
        void returnsFalseForNonEmptyFile() {
            TestFileSource file = new TestFileSource("data.txt", new byte[]{1, 2, 3});
            assertFalse(helper.isFileEmpty(file));
        }
    }

    // ── isFileSizeExceeded ───────────────────────────────────────────

    @Nested
    class IsFileSizeExceeded {

        @Test
        void returnsFalseWhenWithinLimit() {
            TestFileSource file = new TestFileSource("file.txt", new byte[100]);
            assertFalse(helper.isFileSizeExceeded(file, 1024));
        }

        @Test
        void returnsTrueWhenExceedingLimit() {
            TestFileSource file = new TestFileSource("file.txt", new byte[2048]);
            assertTrue(helper.isFileSizeExceeded(file, 1024));
        }

        @Test
        void returnsFalseWhenExactlyAtLimit() {
            TestFileSource file = new TestFileSource("file.txt", new byte[1024]);
            assertFalse(helper.isFileSizeExceeded(file, 1024));
        }

        @Test
        void returnsFalseWhenMaxSizeIsZero_noLimit() {
            TestFileSource file = new TestFileSource("file.txt", new byte[999999]);
            assertFalse(helper.isFileSizeExceeded(file, 0));
        }

        @Test
        void returnsFalseWhenMaxSizeIsNegative() {
            TestFileSource file = new TestFileSource("file.txt", new byte[100]);
            assertFalse(helper.isFileSizeExceeded(file, -1));
        }
    }

    // ── isValidFilename ─────────────────────────────────────────────

    @Nested
    class IsValidFilename {

        @Test
        void validSimpleFilename() {
            assertTrue(helper.isValidFilename(new TestFileSource("photo.jpg", new byte[1])));
        }

        @Test
        void validFilenameWithSpaces() {
            assertTrue(helper.isValidFilename(new TestFileSource("my photo.jpg", new byte[1])));
        }

        @Test
        void validFilenameWithKorean() {
            assertTrue(helper.isValidFilename(new TestFileSource("사진.jpg", new byte[1])));
        }

        @Test
        void rejectsNullFilename() {
            assertFalse(helper.isValidFilename(new TestFileSource(null, new byte[1])));
        }

        @Test
        void rejectsBlankFilename() {
            assertFalse(helper.isValidFilename(new TestFileSource("   ", new byte[1])));
        }

        @Test
        void rejectsEmptyFilename() {
            assertFalse(helper.isValidFilename(new TestFileSource("", new byte[1])));
        }

        @Test
        void rejectsFilenameLongerThan200Chars() {
            String longName = "a".repeat(201) + ".jpg";
            assertFalse(helper.isValidFilename(new TestFileSource(longName, new byte[1])));
        }

        @Test
        void acceptsFilenameExactly200Chars() {
            String name = "a".repeat(196) + ".jpg";
            assertEquals(200, name.length());
            assertTrue(helper.isValidFilename(new TestFileSource(name, new byte[1])));
        }

        @Test
        void rejectsDoubleDot() {
            assertFalse(helper.isValidFilename(new TestFileSource("..", new byte[1])));
        }

        @Test
        void rejectsPathTraversalWithDoubleDot() {
            assertFalse(helper.isValidFilename(new TestFileSource("../etc/passwd", new byte[1])));
        }

        @Test
        void rejectsDoubleDotInMiddle() {
            assertFalse(helper.isValidFilename(new TestFileSource("foo..bar", new byte[1])));
        }

        @Test
        void rejectsForwardSlash() {
            assertFalse(helper.isValidFilename(new TestFileSource("path/file.jpg", new byte[1])));
        }

        @Test
        void rejectsBackslash() {
            assertFalse(helper.isValidFilename(new TestFileSource("path\\file.jpg", new byte[1])));
        }

        @Test
        void acceptsSingleDotInExtension() {
            assertTrue(helper.isValidFilename(new TestFileSource("file.jpg", new byte[1])));
        }

        @Test
        void acceptsMultipleDotsSeparated() {
            // "file.backup.jpg" contains no ".." sequence
            assertTrue(helper.isValidFilename(new TestFileSource("file.backup.jpg", new byte[1])));
        }
    }

    // ── isValidMediaType ────────────────────────────────────────────

    @Nested
    class IsValidMediaType {

        private final Set<SafeMediaType> allowed = Set.of(TestMediaType.JPEG, TestMediaType.PNG);

        @Test
        void returnsTrueForAllowedType() {
            TestFileSource file = new TestFileSource("photo.jpg", new byte[1]);
            assertTrue(helper.isValidMediaType(file, allowed));
        }

        @Test
        void returnsFalseForDisallowedType() {
            TestFileSource file = new TestFileSource("document.pdf", new byte[1]);
            assertFalse(helper.isValidMediaType(file, allowed));
        }

        @Test
        void returnsFalseForUnknownType() {
            TestFileSource file = new TestFileSource("unknown.xyz", new byte[1]);
            assertFalse(helper.isValidMediaType(file, allowed));
        }
    }

    // ── Batch validation methods ────────────────────────────────────

    @Nested
    class BatchValidation {

        private final Set<SafeMediaType> allowed = Set.of(TestMediaType.JPEG, TestMediaType.PNG);

        @Test
        void isAnyFileEmpty_returnsTrueWhenOneEmpty() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[1]),
                    new TestFileSource("b.jpg", new byte[0])
            );
            assertTrue(helper.isAnyFileEmpty(files));
        }

        @Test
        void isAnyFileEmpty_returnsFalseWhenNoneEmpty() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[1]),
                    new TestFileSource("b.jpg", new byte[2])
            );
            assertFalse(helper.isAnyFileEmpty(files));
        }

        @Test
        void isAnyFileEmpty_returnsFalseForEmptyList() {
            assertFalse(helper.isAnyFileEmpty(List.of()));
        }

        @Test
        void isAnyFileSizeExceeded_returnsTrueWhenOneExceeds() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[10]),
                    new TestFileSource("b.jpg", new byte[200])
            );
            assertTrue(helper.isAnyFileSizeExceeded(files, 100));
        }

        @Test
        void isAnyFileSizeExceeded_returnsFalseWhenNoneExceed() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[50]),
                    new TestFileSource("b.jpg", new byte[50])
            );
            assertFalse(helper.isAnyFileSizeExceeded(files, 100));
        }

        @Test
        void isAllValidFilenames_returnsTrueWhenAllValid() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("photo.jpg", new byte[1]),
                    new TestFileSource("image.png", new byte[1])
            );
            assertTrue(helper.isAllValidFilenames(files));
        }

        @Test
        void isAllValidFilenames_returnsFalseWhenOneInvalid() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("photo.jpg", new byte[1]),
                    new TestFileSource("../evil.jpg", new byte[1])
            );
            assertFalse(helper.isAllValidFilenames(files));
        }

        @Test
        void isAllValidFilenames_returnsTrueForEmptyList() {
            assertTrue(helper.isAllValidFilenames(List.of()));
        }

        @Test
        void validateAllMediaTypeAndExtension_returnsNullWhenAllValid() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[1]),
                    new TestFileSource("b.png", new byte[1])
            );
            assertNull(helper.validateAllMediaTypeAndExtension(files, allowed));
        }

        @Test
        void validateAllMediaTypeAndExtension_returnsErrorForFirstInvalid() {
            List<TestFileSource> files = List.of(
                    new TestFileSource("a.jpg", new byte[1]),
                    new TestFileSource("b.pdf", new byte[1])
            );
            assertEquals("file-kit.validation.unsupported-media-type",
                    helper.validateAllMediaTypeAndExtension(files, allowed));
        }

        @Test
        void validateAllMediaTypeAndExtension_returnsNullForEmptyList() {
            assertNull(helper.validateAllMediaTypeAndExtension(List.of(), allowed));
        }
    }

    // ── validateMediaTypeAndExtension ────────────────────────────────

    @Nested
    class ValidateMediaTypeAndExtension {

        private final Set<SafeMediaType> allowed = Set.of(TestMediaType.JPEG, TestMediaType.PNG);

        @Test
        void returnsNullForValidFile() {
            TestFileSource file = new TestFileSource("photo.jpg", new byte[1]);
            assertNull(helper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void returnsUnsupportedMediaTypeForDisallowedType() {
            TestFileSource file = new TestFileSource("document.pdf", new byte[1]);
            assertEquals("file-kit.validation.unsupported-media-type",
                    helper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void returnsInvalidExtensionForNoExtension() {
            // Stub returns octet-stream for extensionless files, which won't be in allowed set
            // But if we set up a scenario where type is valid but extension is missing:
            // We need a file that has a valid type but no extension
            // Using a custom detector to simulate this
            FileValidationHelper customHelper = new FileValidationHelper((filename, is) -> "image/jpeg");
            TestFileSource file = new TestFileSource("photo", new byte[1]);
            assertEquals("file-kit.validation.invalid-extension",
                    customHelper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void returnsInvalidExtensionForNullFilename() {
            FileValidationHelper customHelper = new FileValidationHelper((filename, is) -> "image/jpeg");
            TestFileSource file = new TestFileSource(null, new byte[1]);
            assertEquals("file-kit.validation.invalid-extension",
                    customHelper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void returnsInvalidExtensionForWrongExtension() {
            // File detected as image/jpeg but has .png extension
            FileValidationHelper customHelper = new FileValidationHelper((filename, is) -> "image/jpeg");
            TestFileSource file = new TestFileSource("photo.png", new byte[1]);
            assertEquals("file-kit.validation.invalid-extension",
                    customHelper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void caseInsensitiveExtensionMatching() {
            TestFileSource file = new TestFileSource("photo.JPG", new byte[1]);
            assertNull(helper.validateMediaTypeAndExtension(file, allowed));
        }

        @Test
        void detectsOnlyOnce() {
            int[] callCount = {0};
            MediaTypeDetector countingDetector = (filename, is) -> {
                callCount[0]++;
                return "image/jpeg";
            };
            FileValidationHelper countingHelper = new FileValidationHelper(countingDetector);

            TestFileSource file = new TestFileSource("photo.jpg", new byte[1]);
            countingHelper.validateMediaTypeAndExtension(file, allowed);

            assertEquals(1, callCount[0], "MIME detection should be called exactly once");
        }
    }

}
