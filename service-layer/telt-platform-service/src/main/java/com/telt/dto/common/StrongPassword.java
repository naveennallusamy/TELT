package com.telt.dto.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

/**
 * Password policy: 8-128 characters with at least one upper-case letter, lower-case letter, digit and symbol.
 * {@code null} is valid; combine with {@code @NotBlank} where the password is mandatory.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$")
public @interface StrongPassword {
    String message() default "must be 8-128 characters and include upper-case, lower-case, digit and symbol";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
