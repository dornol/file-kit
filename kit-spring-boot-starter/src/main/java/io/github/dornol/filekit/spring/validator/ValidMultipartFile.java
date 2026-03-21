package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.SafeMediaType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Jakarta Validation constraint annotation for validating Spring {@code MultipartFile} uploads.
 *
 * <p>Use this annotation on {@code MultipartFile}, {@code MultipartFile[]},
 * or {@code Collection<MultipartFile>} parameters:</p>
 *
 * <pre>{@code
 * @PostMapping("/upload")
 * public ResponseEntity<?> upload(
 *         @RequestParam("file")
 *         @ValidMultipartFile(value = AllowedMediaType.class, maxSize = 10 * 1024 * 1024)
 *         MultipartFile file) {
 *     // ...
 * }
 * }</pre>
 *
 * @see io.github.dornol.filekit.validator.ValidFile
 */
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {
        MultipartFileValidator.class,
        MultipartFileArrayValidator.class,
        MultipartFileCollectionValidator.class
})
@Documented
public @interface ValidMultipartFile {
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
