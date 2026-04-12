package io.github.dornol.filekit.image;

import java.util.Objects;

/**
 * Default {@link ThumbnailGenerator} implementation that delegates to {@link ImageResizer}.
 */
public class DefaultThumbnailGenerator implements ThumbnailGenerator {

    private final ImageResizer resizer;

    /** @param resizer the resizer to delegate thumbnail generation to */
    public DefaultThumbnailGenerator(ImageResizer resizer) {
        this.resizer = Objects.requireNonNull(resizer, "resizer");
    }

    @Override
    public ResizeResult generate(byte[] imageBytes, ThumbnailOption option) {
        ResizeOption resizeOption = new ResizeOption(
                option.maxDimension(), option.maxDimension(),
                ScaleMode.FIT, option.outputFormat(), option.quality());
        return resizer.resize(imageBytes, resizeOption);
    }
}
