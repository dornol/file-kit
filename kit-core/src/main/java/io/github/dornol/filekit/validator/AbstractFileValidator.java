package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/**
 * Base class for {@link ValidFile} constraint validators.
 *
 * <p>Delegates the validation flow to {@link BaseFileValidationSupport}
 * and exposes the annotation configuration (allowed media types, max size)
 * to subclasses via protected accessors.</p>
 *
 * @param <T> the type of value being validated (e.g. {@code FileSource})
 * @see FileSourceValidator
 */
public abstract class AbstractFileValidator<T> implements ConstraintValidator<ValidFile, T>, FileValidationCallbacks<T> {

    /** Shared validation support — subclasses use this to access annotation configuration. */
    protected final BaseFileValidationSupport<T> support;

    protected AbstractFileValidator() {
        this.support = new BaseFileValidationSupport<>(this);
    }

    @Override
    public void initialize(ValidFile constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        support.init(constraintAnnotation.value(), constraintAnnotation.maxSize(),
                constraintAnnotation.minWidth(), constraintAnnotation.maxWidth(),
                constraintAnnotation.minHeight(), constraintAnnotation.maxHeight());
    }

    @Override
    public boolean isValid(@Nullable T value, ConstraintValidatorContext context) {
        return support.isValid(value, context);
    }

}
