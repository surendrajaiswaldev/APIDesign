package com.apidesign.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for SKU (Stock Keeping Unit) format.
 *
 * Business Rules for SKU:
 * - Must be alphanumeric (A-Z, 0-9, hyphen allowed)
 * - Length: 3-20 characters
 * - Used for product identification across supply chain
 * - Often follows company-specific format
 *
 * This validator ensures:
 * - SKUs follow a standard format
 * - No special characters that might break integrations
 * - Reasonable length constraints
 *
 * Example:
 * @ValidSku
 * private String sku;  // e.g., "PROD-2024-001"
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SkuValidator.class)
@Documented
public @interface ValidSku {
    /**
     * Default error message.
     */
    String message() default "SKU must be alphanumeric with hyphens only, length 3-20 characters";

    /**
     * Validation groups.
     */
    Class<?>[] groups() default {};

    /**
     * Custom payload.
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum length for SKU.
     */
    int minLength() default 3;

    /**
     * Maximum length for SKU.
     */
    int maxLength() default 20;
}
