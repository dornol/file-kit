package io.github.dornol.filekit.image;

/**
 * Determines how an image is scaled to fit the target dimensions.
 */
public enum ScaleMode {

    /**
     * Scale to fit within the target dimensions, preserving aspect ratio.
     * The result will be at most targetWidth x targetHeight.
     */
    FIT,

    /**
     * Scale to cover the target dimensions, preserving aspect ratio.
     * The result is cropped to exactly targetWidth x targetHeight.
     */
    COVER,

    /**
     * Scale to exactly the target dimensions, ignoring aspect ratio.
     */
    EXACT
}
