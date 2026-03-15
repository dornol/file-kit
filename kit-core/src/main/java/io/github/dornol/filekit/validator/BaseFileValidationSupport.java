package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.Set;

public class BaseFileValidationSupport<T> {

    private final FileValidationCallbacks<T> callbacks;
    private Set<SafeMediaType> allowedMediaTypes;
    private long maxSize;

    public BaseFileValidationSupport(FileValidationCallbacks<T> callbacks) {
        this.callbacks = callbacks;
    }

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

        this.allowedMediaTypes = safeMediaTypes;
        this.maxSize = maxSize;
    }

    public boolean isValid(T value, ConstraintValidatorContext context) {
        if (value == null || callbacks.isValidationNotRequired(value)) {
            return true;
        }
        if (!callbacks.isValidMediaType(value)) {
            applyConstraintViolation(context, "file-kit.validation.file.not-supported");
            return false;
        } else if (callbacks.isFileEmpty(value)) {
            applyConstraintViolation(context, "file-kit.validation.file.empty");
            return false;
        } else if (callbacks.isFileSizeExceeded(value)) {
            applyConstraintViolation(context, "file-kit.validation.file.too-large");
            return false;
        } else if (!callbacks.isValidFilename(value)) {
            applyConstraintViolation(context, "file-kit.validation.file.invalid-filename");
            return false;
        } else if (!callbacks.isValidExtension(value)) {
            applyConstraintViolation(context, "file-kit.validation.file.invalid-extension");
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
