package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Base class for {@link ValidFile} constraint validators.
 *
 * <p>Delegates the validation flow to {@link BaseFileValidationSupport}
 * and exposes the annotation configuration (allowed media types, max size)
 * to subclasses via protected accessors.</p>
 *
 * @param <T> the type of value being validated (e.g. {@link FileSource})
 * @see FileSourceValidator
 */
public abstract class AbstractFileValidator<T> implements ConstraintValidator<ValidFile, T>, FileValidationCallbacks<T> {

    private final BaseFileValidationSupport<T> support;

    protected AbstractFileValidator() {
        this.support = new BaseFileValidationSupport<>(this);
    }

    @Override
    public void initialize(ValidFile constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        support.init(constraintAnnotation.value(), constraintAnnotation.maxSize());
    }

    @Override
    public boolean isValid(T value, ConstraintValidatorContext context) {
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
