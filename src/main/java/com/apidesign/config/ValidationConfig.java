package com.apidesign.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Bean Validation configuration with a single feature flag: {@code app.validation.enabled}.
 *
 * When the flag is {@code true} (default): standard Spring/Jakarta Validation. {@code @Valid}
 * on {@code @RequestBody}, {@code @Validated} on controllers, custom validators, all behave
 * as Spring Boot's autoconfiguration would set them up.
 *
 * When {@code false}: the validator we publish is a {@link LocalValidatorFactoryBean}
 * subclass whose {@code afterPropertiesSet} skips Jakarta provider initialization and whose
 * {@code validate(...)} family of methods is no-op. The method-level constraint processor
 * is similarly neutered. Net effect: existing {@code @Valid} / {@code @Validated}
 * annotations stay in the source tree but produce no errors at runtime.
 *
 * Set {@code app.validation.enabled=false} for fast local iteration; leave the default
 * in dev/prod. The {@code /auth/token} endpoint never had {@code @Valid} to begin with.
 */
@Configuration
public class ValidationConfig {

    @Bean
    @Primary
    public LocalValidatorFactoryBean defaultValidator(
        @Value("${app.validation.enabled:true}") boolean enabled) {
        if (enabled) {
            return new LocalValidatorFactoryBean();
        }
        return new NoOpValidatorFactoryBean();
    }

    /**
     * Validator alias for components asking for {@link Validator} directly. Delegates to
     * the same factory bean so the no-op behavior is honored everywhere.
     */
    @Bean
    public Validator validatorBean(LocalValidatorFactoryBean factoryBean) {
        return factoryBean;
    }

    /**
     * Method-level validation post-processor. When validation is disabled, returns a
     * subclass whose post-processing hooks short-circuit so {@code @Validated} controllers
     * don't get wrapped in a validating proxy.
     */
    @Bean
    @Primary
    public MethodValidationPostProcessor methodValidationPostProcessor(
        @Value("${app.validation.enabled:true}") boolean enabled) {
        if (enabled) {
            return new MethodValidationPostProcessor();
        }
        return new NoOpMethodValidationPostProcessor();
    }

    /** Validator that accepts everything. Used when validation is globally disabled. */
    static final class NoOpValidatorFactoryBean extends LocalValidatorFactoryBean {

        @Override
        public void afterPropertiesSet() {
            // Skip Jakarta provider initialization — there's nothing to wire.
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public void validate(Object target, Errors errors) {
            // no-op
        }

        @Override
        public void validate(Object target, Errors errors, Object... validationHints) {
            // no-op
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
            return Set.of();
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateProperty(
            T object, String propertyName, Class<?>... groups) {
            return Set.of();
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateValue(
            Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
            return Set.of();
        }
    }

    /**
     * Method-validation post-processor that returns beans untouched. Spring Boot
     * auto-configures one; we override it via {@code @Primary} so this neutered version
     * wins when {@code app.validation.enabled=false}.
     */
    static final class NoOpMethodValidationPostProcessor extends MethodValidationPostProcessor {

        @Override
        public void afterPropertiesSet() {
            // Skip advisor construction so no method-validation proxy is registered.
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            return bean;
        }
    }
}
