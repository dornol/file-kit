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

class ImageIOCropperTest {

    private final ImageIOCropper cropper = new ImageIOCropper();

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

    // C1
    @Test
    void centerCrop_exactDimensions() {
        byte[] input = image(200, 200, "png");

        CropResult result = cropper.crop(input, CropOption.of(50, 50, 100, 100));

        BufferedImage out = decode(result.data());
        assertEquals(100, out.getWidth());
        assertEquals(100, out.getHeight());
        assertEquals(100, result.metadata().width());
    }

    // C2
    @Test
    void originCrop_works() {
        byte[] input = image(200, 200, "png");

        CropResult result = cropper.crop(input, CropOption.of(0, 0, 50, 50));

        assertEquals(50, result.metadata().width());
        assertEquals(50, result.metadata().height());
    }

    // C3
    @Test
    void regionExceedsBounds_throws() {
        byte[] input = image(100, 100, "png");

        assertThrows(IllegalArgumentException.class,
                () -> cropper.crop(input, CropOption.of(50, 50, 80, 80)));
    }

    // C3b — width overflow alone
    @Test
    void widthExceedsBounds_throws() {
        byte[] input = image(100, 100, "png");

        assertThrows(IllegalArgumentException.class,
                () -> cropper.crop(input, CropOption.of(0, 0, 200, 50)));
    }

    // C4
    @Test
    void option_negativeX_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CropOption.of(-1, 0, 10, 10));
    }

    // C5
    @Test
    void option_zeroWidth_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CropOption.of(0, 0, 0, 10));
    }

    // C5b
    @Test
    void option_negativeHeight_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CropOption.of(0, 0, 10, -5));
    }

    // C5c
    @Test
    void option_qualityOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new CropOption(0, 0, 10, 10, null, 2.0f));
    }

    // C6
    @Test
    void explicitOutputFormat_usedInResult() {
        byte[] input = image(200, 200, "png");

        CropResult result = cropper.crop(input,
                new CropOption(10, 10, 50, 50, "jpeg", 0.9f));

        assertEquals("jpeg", result.metadata().format());
    }

    // C7
    @Test
    void corruptedImage_throwsFileStorageException() {
        byte[] garbage = {0x00, 0x01, 0x02, 0x03};

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> cropper.crop(garbage, CropOption.of(0, 0, 10, 10)));
        assertEquals(FileStorageException.IMAGE_PROCESSING_FAILED, ex.getMessageKey());
    }

    // C8
    @Test
    void output_hasBytes() {
        byte[] input = image(100, 100, "png");
        CropResult result = cropper.crop(input, CropOption.of(0, 0, 50, 50));
        assertTrue(result.data().length > 0);
    }
}
