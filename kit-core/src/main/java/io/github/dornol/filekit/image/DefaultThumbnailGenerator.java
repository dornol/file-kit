package io.github.dornol.filekit.image;

/**
 * Default {@link ThumbnailGenerator} implementation that delegates to {@link ImageResizer}.
 */
public class DefaultThumbnailGenerator implements ThumbnailGenerator {

    private final ImageResizer resizer;

    public DefaultThumbnailGenerator(ImageResizer resizer) {
        this.resizer = resizer;
    }

    @Override
    public ResizeResult generate(byte[] imageBytes, ThumbnailOption option) {
        ResizeOption resizeOption = new ResizeOption(
                option.maxDimension(), option.maxDimension(),
                ScaleMode.FIT, option.outputFormat(), option.quality());
        return resizer.resize(imageBytes, resizeOption);
    }
}
