package io.github.dornol.filekit.validator;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageDimensionValidatorTest {

    private final ImageDimensionValidator validator = new ImageDimensionValidator();

    private static TestFileSource image(int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return new TestFileSource("image.png", baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // I1
    @Test
    void withinConstraints_returnsNull() {
        assertNull(validator.validate(image(200, 150), 100, 300, 100, 200));
    }

    // I2
    @Test
    void widthTooSmall() {
        assertEquals(ValidationMessageKeys.IMAGE_WIDTH_TOO_SMALL,
                validator.validate(image(50, 100), 100, 0, 0, 0));
    }

    // I3
    @Test
    void widthTooLarge() {
        assertEquals(ValidationMessageKeys.IMAGE_WIDTH_TOO_LARGE,
                validator.validate(image(500, 100), 0, 300, 0, 0));
    }

    // I4
    @Test
    void heightTooSmall() {
        assertEquals(ValidationMessageKeys.IMAGE_HEIGHT_TOO_SMALL,
                validator.validate(image(100, 50), 0, 0, 100, 0));
    }

    // I5
    @Test
    void heightTooLarge() {
        assertEquals(ValidationMessageKeys.IMAGE_HEIGHT_TOO_LARGE,
                validator.validate(image(100, 500), 0, 0, 0, 300));
    }

    // I6
    @Test
    void zeroConstraints_anyDimensionOk() {
        assertNull(validator.validate(image(1, 1), 0, 0, 0, 0));
        assertNull(validator.validate(image(2_000, 2_000), 0, 0, 0, 0));
    }

    // I7
    @Test
    void nonImage_returnsNotReadable() {
        TestFileSource file = new TestFileSource("doc.txt", "plain text".getBytes());
        assertEquals(ValidationMessageKeys.IMAGE_NOT_READABLE,
                validator.validate(file, 0, 0, 0, 0));
    }

    // I8
    @Test
    void validateAll_returnsFirstFailure() {
        TestFileSource ok = image(200, 200);
        TestFileSource tooSmall = image(50, 200);
        TestFileSource okAgain = image(200, 200);
        assertEquals(ValidationMessageKeys.IMAGE_WIDTH_TOO_SMALL,
                validator.validateAll(List.of(ok, tooSmall, okAgain), 100, 0, 0, 0));
    }

    // I9
    @Test
    void validateAll_allValid_returnsNull() {
        assertNull(validator.validateAll(
                List.of(image(200, 200), image(250, 250)), 100, 300, 100, 300));
    }

    // I10
    @Test
    void nullValue_throws() {
        assertThrows(NullPointerException.class,
                () -> validator.validate(null, 0, 0, 0, 0));
    }

    // I11
    @Test
    void validateAll_nullFiles_throws() {
        assertThrows(NullPointerException.class,
                () -> validator.validateAll(null, 0, 0, 0, 0));
    }
}
