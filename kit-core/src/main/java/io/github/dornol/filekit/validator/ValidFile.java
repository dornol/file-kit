package io.github.dornol.filekit.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE, METHOD, FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface ValidFile {
    String message() default "Invalid file";

    long maxSize() default 0L;

    Class<? extends Enum<? extends SafeMediaType>>[] value() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
