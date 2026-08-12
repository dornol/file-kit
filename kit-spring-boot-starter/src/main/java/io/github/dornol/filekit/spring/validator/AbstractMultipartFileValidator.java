package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.BaseFileValidationSupport;
import io.github.dornol.filekit.validator.FileValidationCallbacks;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

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

    /** Shared validation support — subclasses use this to access annotation configuration. */
    protected final BaseFileValidationSupport<T> support;

    protected AbstractMultipartFileValidator() {
        this(0);
    }

    protected AbstractMultipartFileValidator(long defaultMaxSize) {
        this.support = new BaseFileValidationSupport<>(this, defaultMaxSize);
    }

    @Override
    public void initialize(ValidMultipartFile constraintAnnotation) {
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
