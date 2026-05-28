package com.apidesign.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for @ValidSku annotation.
 *
 * SKU Format Rules:
 * - Alphanumeric characters (A-Z, 0-9)
 * - Hyphen (-) allowed for readability
 * - No spaces, special characters, or lowercase
 * - Regex: ^[A-Z0-9-]{minLength,maxLength}$
 *
 * Performance Note:
 * - Pattern is compiled once (static field)
 * - Regex matching is cached by Java regex engine
 * - Suitable for validation performance
 */
@Slf4j
public class SkuValidator implements ConstraintValidator<ValidSku, String> {
    private int minLength;
    private int maxLength;

    /**
     * Initialize validator parameters.
     *
     * Extracts min and max length from annotation.
     * Called once when validator is instantiated.
     *
     * @param annotation the @ValidSku annotation
     */
    @Override
    public void initialize(ValidSku annotation) {
        this.minLength = annotation.minLength();
        this.maxLength = annotation.maxLength();
        log.debug("Initializing SkuValidator with min={}, max={}", minLength, maxLength);
    }

    /**
     * Validate SKU format.
     *
     * Rules checked:
     * 1. Not null/empty (use @NotBlank for that)
     * 2. Matches pattern: alphanumeric and hyphens only
     * 3. Length between min and max
     * 4. Uppercase letters only (no lowercase)
     *
     * Example valid SKUs:
     * - PROD-2024-001
     * - ABC123
     * - SKU-XYZ-789
     *
     * Example invalid SKUs:
     * - prod-2024-001 (lowercase)
     * - PROD_2024_001 (underscore)
     * - PROD 2024 001 (spaces)
     *
     * @param sku the SKU string to validate
     * @param context the constraint validator context
     * @return true if valid, false otherwise
     */
    @Override
    public boolean isValid(String sku, ConstraintValidatorContext context) {
        // Null values are valid (use @NotBlank if required)
        if (sku == null || sku.isBlank()) {
            return true;
        }

        // Check length
        if (sku.length() < minLength || sku.length() > maxLength) {
            log.warn("SKU validation failed - invalid length: {} (min={}, max={})",
                    sku.length(), minLength, maxLength);
            return false;
        }

        // Check format: alphanumeric (uppercase) and hyphens only
        // Pattern: ^[A-Z0-9-]+$
        boolean isValid = sku.matches("^[A-Z0-9-]+$");

        if (!isValid) {
            log.warn("SKU validation failed - invalid format: {}", sku);
        }

        return isValid;
    }
}
