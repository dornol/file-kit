package io.github.dornol.filekit.image;

/**
 * Discrete rotation angles supported by {@link ImageRotator}. Limited to 90°
 * multiples so the output image has the same pixel grid alignment as the
 * source (no anti-aliasing artifacts, no empty corners).
 *
 * @since 0.1.24
 */
public enum RotateAngle {

    DEGREES_90(90),
    DEGREES_180(180),
    DEGREES_270(270);

    private final int degrees;

    RotateAngle(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }
}
