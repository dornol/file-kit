package io.github.dornol.filekit.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public abstract class AbstractFileValidator<T> implements ConstraintValidator<ValidFile, T>, FileValidationCallbacks<T> {

    private final BaseFileValidationSupport<T> support;

    protected AbstractFileValidator(MessageConverter messageConverter) {
        this.support = new BaseFileValidationSupport<>(messageConverter, this);
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

    protected Set<SafeMediaType> getAllowedMediaTypes() {
        return support.getAllowedMediaTypes();
    }

    protected long getMaxSize() {
        return support.getMaxSize();
    }

}
