package com.apidesign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Bean Validation (Jakarta Validation/JSR-380) Configuration.
 *
 * Bean Validation Framework:
 * =========================
 *
 * What is Bean Validation?
 * - Standard Java API for validating objects (JSR-303/JSR-380)
 * - Declarative approach using annotations
 * - Automatic validation at controller layer
 * - Framework-agnostic (works with any Java framework)
 *
 * Common Annotations:
 * - @NotNull: Field must not be null
 * - @NotBlank: String must not be blank
 * - @NotEmpty: Collection/String must not be empty
 * - @Email: Valid email format
 * - @Positive: Number must be positive
 * - @Min/@Max: Numeric range validation
 * - @Size: String/Collection length validation
 * - @Pattern: Regex pattern matching
 *
 * Custom Validators:
 * - Implement ConstraintValidator<Annotation, Type>
 * - Examples in this project:
 *   * @ValidSku with SkuValidator (alphanumeric SKU format)
 *   * @PositiveBigDecimal with PositiveBigDecimalValidator (BigDecimal > 0)
 *
 * Validation Layers:
 * 1. Bean Level (@Valid in controller):
 *    - Validates entire request object
 *    - Spring triggers on @Valid @RequestBody
 *    - Violations mapped to 400 Bad Request
 *
 * 2. Method Level (@Param validation):
 *    - @Validated on controller class
 *    - @Positive/@Email on path/query parameters
 *
 * 3. Cross-field Validation:
 *    - Create custom class-level validators
 *    - Example: Start date before end date
 *    - Applied as @Validated on class level
 *
 * How Spring Validation Works:
 * ===========================
 * 1. Controller receives @RequestBody with @Valid
 * 2. Spring invokes validator on the object
 * 3. Validator checks all @Constraint annotations
 * 4. Violations collected in BindingResult
 * 5. If violations exist:
 *    - MethodArgumentNotValidException thrown
 *    - GlobalExceptionHandler catches and formats response
 *    - 400 Bad Request returned to client
 * 6. If no violations:
 *    - Proceed with method execution
 *
 * Validation Best Practices:
 * =========================
 * 1. Validate at Input Boundaries
 *    - Always validate @RequestBody
 *    - Validate path/query parameters if needed
 *    - Don't rely on client-side validation
 *
 * 2. Use Standard Annotations First
 *    - @Email, @Positive, @Pattern cover most cases
 *    - Only create custom validators when necessary
 *
 * 3. Meaningful Error Messages
 *    - @NotBlank(message = "Email is required")
 *    - Users get clear guidance on what's wrong
 *
 * 4. Group Validations
 *    - Use validation groups for different scenarios
 *    - Example: CREATE vs UPDATE have different rules
 *    - Using: @Valid(groups = CreateView.class)
 *
 * 5. Performance Considerations
 *    - Validation is synchronous (happens on request)
 *    - Complex regex patterns can impact performance
 *    - Cache validation results when appropriate
 *
 * Configuration in Spring Boot:
 * ============================
 * - Auto-configures LocalValidatorFactoryBean if Validator on classpath
 * - Spring Boot starter-validation includes Jakarta Validation
 * - Manual bean definition shown here for documentation
 */
@Configuration
public class ValidationConfig {

    /**
     * Register the Validator bean for Spring dependency injection.
     *
     * LocalValidatorFactoryBean:
     * - Factory for creating Validator instances
     * - Integrates with Spring's @Valid annotation processing
     * - Supports MessageInterpolation for error messages
     * - Loads validators from META-INF/services
     *
     * The bean is used by:
     * - @Valid on @RequestBody in controllers
     * - MethodValidationPostProcessor for @Validated methods
     * - Custom validation code that needs Validator.validate()
     *
     * @return configured validator instance
     */
    @Bean
    public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }

    /**
     * Register the Validator instance (Facade for LocalValidatorFactoryBean).
     *
     * Jakarta Validation provides this Validator interface.
     * Enables manual validation when needed:
     *
     * Example usage:
     * @Autowired
     * private Validator validator;
     *
     * public void validateManually(MyObject obj) {
     *     Set<ConstraintViolation<MyObject>> violations = validator.validate(obj);
     *     violations.forEach(v -> log.error(v.getMessage()));
     * }
     *
     * @param factoryBean the validator factory bean
     * @return the validator instance
     */
    @Bean
    public Validator validatorBean(org.springframework.validation.beanvalidation.LocalValidatorFactoryBean factoryBean) {
        return factoryBean.getValidator();
    }
}

