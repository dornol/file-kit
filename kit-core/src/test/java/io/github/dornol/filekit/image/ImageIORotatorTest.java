package io.github.dornol.filekit.image;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageIORotatorTest {

    private final ImageIORotator rotator = new ImageIORotator();

    private static byte[] image(int width, int height, String format) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // R1
    @Test
    void rotate90_swapsDimensions() {
        byte[] input = image(200, 100, "png");

        RotateResult result = rotator.rotate(input, RotateOption.of(RotateAngle.DEGREES_90));

        BufferedImage out = decode(result.data());
        assertEquals(100, out.getWidth());
        assertEquals(200, out.getHeight());
        assertEquals(100, result.metadata().width());
        assertEquals(200, result.metadata().height());
    }

    // R2
    @Test
    void rotate180_keepsDimensions() {
        byte[] input = image(200, 100, "png");

        RotateResult result = rotator.rotate(input, RotateOption.of(RotateAngle.DEGREES_180));

        BufferedImage out = decode(result.data());
        assertEquals(200, out.getWidth());
        assertEquals(100, out.getHeight());
    }

    // R3
    @Test
    void rotate270_swapsDimensions() {
        byte[] input = image(200, 100, "png");

        RotateResult result = rotator.rotate(input, RotateOption.of(RotateAngle.DEGREES_270));

        BufferedImage out = decode(result.data());
        assertEquals(100, out.getWidth());
        assertEquals(200, out.getHeight());
    }

    // R4
    @Test
    void nullOutputFormat_keepsOriginalFormat() {
        byte[] input = image(100, 100, "png");

        RotateResult result = rotator.rotate(input, RotateOption.of(RotateAngle.DEGREES_90));

        assertEquals("png", result.metadata().format());
    }

    // R5
    @Test
    void explicitOutputFormat_usedInResult() {
        byte[] input = image(100, 100, "png");

        RotateResult result = rotator.rotate(input,
                new RotateOption(RotateAngle.DEGREES_90, "jpeg", 0.9f));

        assertEquals("jpeg", result.metadata().format());
    }

    // R6
    @Test
    void corruptedImage_throwsFileStorageException() {
        byte[] garbage = {0x00, 0x01, 0x02, 0x03};

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> rotator.rotate(garbage, RotateOption.of(RotateAngle.DEGREES_90)));
        assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
    }

    // R7
    @Test
    void option_nullAngle_throws() {
        assertThrows(NullPointerException.class,
                () -> new RotateOption(null, null, 0.85f));
    }

    // R8
    @Test
    void option_qualityOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RotateOption(RotateAngle.DEGREES_90, null, 1.5f));
        assertThrows(IllegalArgumentException.class,
                () -> new RotateOption(RotateAngle.DEGREES_90, null, -0.1f));
    }

    // R9 — enum angle values
    @Test
    void rotateAngle_degreesAccessor() {
        assertEquals(90, RotateAngle.DEGREES_90.degrees());
        assertEquals(180, RotateAngle.DEGREES_180.degrees());
        assertEquals(270, RotateAngle.DEGREES_270.degrees());
    }

    // R10 — output non-empty
    @Test
    void output_hasBytes() {
        byte[] input = image(50, 50, "png");
        RotateResult result = rotator.rotate(input, RotateOption.of(RotateAngle.DEGREES_90));
        assertTrue(result.data().length > 0);
    }
}
