package com.apidesign.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for positive BigDecimal values.
 *
 * Use Cases:
 * - Product prices (must be > 0)
 * - Order amounts
 * - Any monetary values that must be positive
 *
 * Example:
 * @PositiveBigDecimal(message = "Price must be greater than 0")
 * private BigDecimal price;
 *
 * Benefits over @Positive:
 * - Works specifically with BigDecimal
 * - Clear intent in code
 * - Customizable error messages
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiveBigDecimalValidator.class)
@Documented
public @interface PositiveBigDecimal {
    /**
     * Default error message when validation fails.
     */
    String message() default "Value must be greater than zero";

    /**
     * Validation groups this constraint belongs to.
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients to assign custom error payload.
     */
    Class<? extends Payload>[] payload() default {};
}
