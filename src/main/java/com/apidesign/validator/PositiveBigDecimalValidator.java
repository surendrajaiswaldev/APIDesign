package com.apidesign.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Validator implementation for @PositiveBigDecimal annotation.
 *
 * ConstraintValidator lifecycle:
 * 1. Spring instantiates validator bean
 * 2. initialize() is called with annotation metadata
 * 3. isValid() is called for each field/parameter with annotation
 * 4. Bean is cached and reused for performance
 *
 * Performance Considerations:
 * - Validators are stateless and thread-safe
 * - Spring caches validators per annotation
 * - Initialize method called only once per lifecycle
 * - isValid() called repeatedly, so keep it lightweight
 */
@Slf4j
public class PositiveBigDecimalValidator implements ConstraintValidator<PositiveBigDecimal, BigDecimal> {

    /**
     * Initialize validator with annotation data.
     *
     * Called once when validator is first used.
     * Can extract custom values from annotation if needed.
     *
     * @param annotation the annotation instance
     */
    @Override
    public void initialize(PositiveBigDecimal annotation) {
        // No special initialization needed for this validator
        log.debug("Initializing PositiveBigDecimalValidator");
    }

    /**
     * Validate that BigDecimal value is greater than zero.
     *
     * Null handling:
     * - Null values are considered valid by Jakarta Validation
     * - Use @NotNull if null should be invalid
     * - This allows optional positive values
     *
     * @param value the BigDecimal value to validate
     * @param context the constraint validator context
     * @return true if valid (value > 0), false otherwise
     */
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        // Null values are valid (use @NotNull if required)
        if (value == null) {
            return true;
        }

        // Check if value is greater than zero
        boolean isValid = value.compareTo(BigDecimal.ZERO) > 0;

        if (!isValid) {
            log.warn("Positive BigDecimal validation failed for value: {}", value);
        }

        return isValid;
    }
}
