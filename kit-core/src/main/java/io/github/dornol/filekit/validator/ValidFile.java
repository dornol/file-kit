package io.github.dornol.filekit.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Jakarta Validation constraint annotation for validating uploaded files.
 *
 * <p>This is the framework-agnostic annotation for use with {@code FileSource}.
 * For Spring's {@code MultipartFile}, use
 * {@code ValidMultipartFile} from the Spring module instead.</p>
 *
 * <p>Supported validation checks:</p>
 * <ul>
 *   <li>Media type detection against allowed types</li>
 *   <li>File emptiness</li>
 *   <li>Maximum file size</li>
 *   <li>Filename safety (path traversal, length)</li>
 *   <li>Extension-to-content consistency</li>
 *   <li>Image dimensions (width/height min/max)</li>
 * </ul>
 */
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {FileSourceValidator.class, FileSourceArrayValidator.class, FileSourceCollectionValidator.class})
@Documented
public @interface ValidFile {
    String message() default "Invalid file";

    /**
     * Maximum allowed file size in bytes. {@code 0} means no limit.
     */
    long maxSize() default 0L;

    /**
     * Minimum required image width in pixels. {@code 0} means no limit.
     * Only applied to image files; non-image files fail validation if any dimension constraint is set.
     */
    int minWidth() default 0;

    /**
     * Maximum allowed image width in pixels. {@code 0} means no limit.
     */
    int maxWidth() default 0;

    /**
     * Minimum required image height in pixels. {@code 0} means no limit.
     */
    int minHeight() default 0;

    /**
     * Maximum allowed image height in pixels. {@code 0} means no limit.
     */
    int maxHeight() default 0;

    /**
     * Enum classes implementing {@link SafeMediaType} that define the allowed media types.
     */
    Class<? extends Enum<? extends SafeMediaType>>[] value() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
