package io.github.dornol.filekit.test;

import jakarta.validation.ConstraintValidatorContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared test utilities for validator tests.
 */
public final class ValidatorTestSupport {

    private ValidatorTestSupport() {}

    /**
     * Creates a mock {@link ConstraintValidatorContext} with a stub violation builder.
     */
    public static ConstraintValidatorContext mockContext() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        return context;
    }
}
