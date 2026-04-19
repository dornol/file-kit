package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaTypeValidatorTest {

    private final MediaTypeValidator validator = new MediaTypeValidator(new StubMediaTypeDetector());
    private final Set<SafeMediaType> allowed = Set.of(TestMediaType.JPEG, TestMediaType.PNG);

    // M1
    @Test
    void validType_validExt_returnsNull() {
        TestFileSource file = new TestFileSource("photo.jpg", "content".getBytes());
        assertNull(validator.validate(file, allowed));
    }

    // M2
    @Test
    void unsupportedType_returnsUnsupportedKey() {
        TestFileSource file = new TestFileSource("doc.pdf", "content".getBytes());
        assertEquals(ValidationMessageKeys.UNSUPPORTED_MEDIA_TYPE,
                validator.validate(file, allowed));
    }

    // M3
    @Test
    void nullFilename_returnsInvalidExtension() {
        TestFileSource file = new TestFileSource(null, "content".getBytes());
        // stub returns "application/octet-stream" which is not in allowed, so UNSUPPORTED_MEDIA_TYPE
        // To hit INVALID_EXTENSION path, detected type must match allowed. Use detector that always
        // returns image/jpeg regardless of filename.
        MediaTypeValidator custom = new MediaTypeValidator((fn, is) -> "image/jpeg");
        assertEquals(ValidationMessageKeys.INVALID_EXTENSION,
                custom.validate(file, allowed));
    }

    // M4
    @Test
    void noExtension_returnsInvalidExtension() {
        TestFileSource file = new TestFileSource("photo", "content".getBytes());
        MediaTypeValidator custom = new MediaTypeValidator((fn, is) -> "image/jpeg");
        assertEquals(ValidationMessageKeys.INVALID_EXTENSION,
                custom.validate(file, allowed));
    }

    // M5
    @Test
    void uppercaseExtension_allowed() {
        TestFileSource file = new TestFileSource("PHOTO.JPG", "content".getBytes());
        MediaTypeValidator custom = new MediaTypeValidator((fn, is) -> "image/jpeg");
        assertNull(custom.validate(file, allowed));
    }

    // M6
    @Test
    void typeAllowedButExtensionNot_returnsInvalidExtension() {
        TestFileSource file = new TestFileSource("photo.bmp", "content".getBytes());
        MediaTypeValidator custom = new MediaTypeValidator((fn, is) -> "image/jpeg");
        assertEquals(ValidationMessageKeys.INVALID_EXTENSION,
                custom.validate(file, allowed));
    }

    // M7
    @Test
    void detectorIOException_wrapsInIllegalState() {
        MediaTypeDetector throwing = (filename, is) -> {
            throw new IOException("boom");
        };
        MediaTypeValidator custom = new MediaTypeValidator(throwing);
        TestFileSource file = new TestFileSource("x.jpg", "content".getBytes());

        assertThrows(IllegalStateException.class,
                () -> custom.validate(file, allowed));
    }

    // M8
    @Test
    void validateAll_returnsFirstFailure() {
        TestFileSource ok = new TestFileSource("a.jpg", "c".getBytes());
        TestFileSource bad = new TestFileSource("b.pdf", "c".getBytes());
        TestFileSource okAgain = new TestFileSource("d.png", "c".getBytes());
        assertEquals(ValidationMessageKeys.UNSUPPORTED_MEDIA_TYPE,
                validator.validateAll(List.of(ok, bad, okAgain), allowed));
    }

    // M9
    @Test
    void validateAll_allValid_returnsNull() {
        TestFileSource a = new TestFileSource("a.jpg", "c".getBytes());
        TestFileSource b = new TestFileSource("b.png", "c".getBytes());
        assertNull(validator.validateAll(List.of(a, b), allowed));
    }

    // M10
    @Test
    void constructor_nullDetector_throws() {
        assertThrows(NullPointerException.class, () -> new MediaTypeValidator(null));
    }

    // M11
    @Test
    void validate_nullValue_throws() {
        assertThrows(NullPointerException.class, () -> validator.validate(null, allowed));
    }

    // M12
    @Test
    void validate_nullAllowed_throws() {
        TestFileSource file = new TestFileSource("a.jpg", "c".getBytes());
        assertThrows(NullPointerException.class, () -> validator.validate(file, null));
    }
}
