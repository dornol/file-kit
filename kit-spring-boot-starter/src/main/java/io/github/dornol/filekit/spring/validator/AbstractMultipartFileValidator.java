package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.BaseFileValidationSupport;
import io.github.dornol.filekit.validator.FileValidationCallbacks;
import io.github.dornol.filekit.validator.SafeMediaType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Base class for {@link ValidMultipartFile} constraint validators.
 *
 * <p>Delegates the validation flow to {@link BaseFileValidationSupport}
 * and exposes the annotation configuration to subclasses.</p>
 *
 * @param <T> the type being validated (e.g. {@code MultipartFile}, {@code MultipartFile[]})
 * @see MultipartFileValidator
 * @see MultipartFileArrayValidator
 * @see MultipartFileCollectionValidator
 */
public abstract class AbstractMultipartFileValidator<T> implements ConstraintValidator<ValidMultipartFile, T>, FileValidationCallbacks<T> {

    private final BaseFileValidationSupport<T> support;

    protected AbstractMultipartFileValidator() {
        this.support = new BaseFileValidationSupport<>(this);
    }

    @Override
    public void initialize(ValidMultipartFile constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        support.init(constraintAnnotation.value(), constraintAnnotation.maxSize());
    }

    @Override
    public boolean isValid(@Nullable T value, ConstraintValidatorContext context) {
        return support.isValid(value, context);
    }

    /** Returns the set of allowed media types configured on the annotation. */
    protected Set<SafeMediaType> getAllowedMediaTypes() {
        return support.getAllowedMediaTypes();
    }

    /** Returns the maximum file size configured on the annotation (0 = no limit). */
    protected long getMaxSize() {
        return support.getMaxSize();
    }

}
