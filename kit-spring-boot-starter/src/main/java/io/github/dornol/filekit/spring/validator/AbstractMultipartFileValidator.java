package io.github.dornol.filekit.spring.validator;

import io.github.dornol.filekit.validator.BaseFileValidationSupport;
import io.github.dornol.filekit.validator.FileValidationCallbacks;
import io.github.dornol.filekit.validator.SafeMediaType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

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
