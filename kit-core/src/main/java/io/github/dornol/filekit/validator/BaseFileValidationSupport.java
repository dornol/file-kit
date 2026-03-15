package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared validation logic used by both {@link AbstractFileValidator} and
 * Spring's {@code AbstractMultipartFileValidator}.
 *
 * <p>Executes each validation check via {@link FileValidationCallbacks} and
 * applies the appropriate constraint violation message on failure.
 * Validation messages use Jakarta Validation's standard message interpolation
 * with keys like {@code {file-kit.validation.unsupported-media-type}}.</p>
 *
 * <p>Validation order: empty &rarr; size &rarr; filename &rarr; media type + extension.
 * Lightweight checks run first to avoid unnecessary I/O for invalid files.</p>
 *
 * @param <T> the type of value being validated
 */
public class BaseFileValidationSupport<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseFileValidationSupport.class);

    private final FileValidationCallbacks<T> callbacks;
    private Set<SafeMediaType> allowedMediaTypes;
    private long maxSize;

    public BaseFileValidationSupport(FileValidationCallbacks<T> callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Initializes allowed media types and max size from the constraint annotation attributes.
     *
     * @param mediaTypeClasses enum classes implementing {@link SafeMediaType}
     * @param maxSize          maximum file size in bytes (0 = no limit)
     */
    public void init(Class<? extends Enum<? extends SafeMediaType>>[] mediaTypeClasses, long maxSize) {
        Set<SafeMediaType> safeMediaTypes = new HashSet<>();

        for (Class<? extends Enum<? extends SafeMediaType>> enumClass : mediaTypeClasses) {
            Enum<?>[] constants = enumClass.getEnumConstants();

            if (constants == null) {
                continue;
            }

            for (Enum<?> constant : constants) {
                safeMediaTypes.add((SafeMediaType) constant);
            }
        }

        this.allowedMediaTypes = Collections.unmodifiableSet(safeMediaTypes);
        this.maxSize = maxSize;

        log.debug("Initialized file validation: allowedMediaTypes={}, maxSize={}", safeMediaTypes, maxSize);
    }

    /**
     * Runs all validation checks against the given value.
     *
     * <p>Validation order: empty &rarr; size &rarr; filename &rarr; media type + extension.
     * Lightweight checks run first to avoid unnecessary MIME detection I/O.</p>
     *
     * @param value   the value to validate
     * @param context the constraint validator context
     * @return {@code true} if the value passes all checks
     */
    public boolean isValid(T value, ConstraintValidatorContext context) {
        if (value == null || callbacks.isValidationNotRequired(value)) {
            return true;
        }

        if (callbacks.isFileEmpty(value)) {
            log.debug("Validation failed: file is empty");
            applyConstraintViolation(context, "file-kit.validation.file-empty");
            return false;
        }

        if (callbacks.isFileSizeExceeded(value)) {
            log.debug("Validation failed: file size exceeded (maxSize={})", maxSize);
            applyConstraintViolation(context, "file-kit.validation.file-too-large");
            return false;
        }

        if (!callbacks.isValidFilename(value)) {
            log.debug("Validation failed: invalid filename");
            applyConstraintViolation(context, "file-kit.validation.invalid-filename");
            return false;
        }

        String mediaTypeError = callbacks.validateMediaTypeAndExtension(value);
        if (mediaTypeError != null) {
            log.debug("Validation failed: {}", mediaTypeError);
            applyConstraintViolation(context, mediaTypeError);
            return false;
        }

        return true;
    }

    public Set<SafeMediaType> getAllowedMediaTypes() {
        return allowedMediaTypes;
    }

    public long getMaxSize() {
        return maxSize;
    }

    private void applyConstraintViolation(ConstraintValidatorContext context, String messageKey) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{" + messageKey + "}").addConstraintViolation();
    }

}
