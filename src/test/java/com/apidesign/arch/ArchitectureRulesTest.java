package com.apidesign.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Static architectural invariants. Each rule encodes a discipline that the project has
 * agreed on; breaking one fails the build.
 *
 * <p>Run with {@code mvn test -Dtest=ArchitectureRulesTest}.
 */
@AnalyzeClasses(packages = "com.apidesign", importOptions = {})
class ArchitectureRulesTest {

    /** Controllers must talk to services, never reach into the persistence layer directly. */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..");

    /** Domain entities cannot import DTOs — the mapping direction is DTO → entity, never both. */
    @ArchTest
    static final ArchRule entities_should_not_depend_on_dtos =
        noClasses()
            .that()
            .resideInAPackage("..entity..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..dto..");

    /** Service-layer code is a downstream of controllers; it must never call back. */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
        noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..");

    /**
     * Repository interfaces must be discoverable by Spring — either {@code @Repository}-annotated
     * or implementing {@link JpaRepository}. Catches a class accidentally dropped into the
     * repository package that won't be wired up.
     */
    @ArchTest
    static final ArchRule repositories_must_be_annotated_or_extend_jpa_repository =
        classes()
            .that()
            .resideInAPackage("..repository..")
            .and()
            .areInterfaces()
            .should(beAnnotatedRepositoryOrJpaSubtype());

    /** No System.out.println — use SLF4J. */
    @ArchTest
    static final ArchRule no_system_out =
        noClasses()
            .should()
            .callMethodWhere(targetsSystemOut())
            .because("Use SLF4J — System.out skips MDC and log levels");

    private static DescribedPredicate<JavaMethodCall> targetsSystemOut() {
        return new DescribedPredicate<>("targets java.lang.System.out") {
            @Override
            public boolean test(JavaMethodCall call) {
                return call.getTarget().getOwner().getName().equals("java.lang.System")
                    && call.getTarget().getName().equals("out");
            }
        };
    }

    private static ArchCondition<JavaClass> beAnnotatedRepositoryOrJpaSubtype() {
        return new ArchCondition<>("be annotated @Repository or assignable to JpaRepository") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                boolean isJpaRepo = clazz.isAssignableTo(JpaRepository.class);
                boolean hasAnnotation = clazz.isAnnotatedWith(Repository.class);
                if (!isJpaRepo && !hasAnnotation) {
                    events.add(SimpleConditionEvent.violated(
                        clazz,
                        clazz.getName()
                            + " in repository package is neither @Repository nor a JpaRepository subtype"));
                }
            }
        };
    }
}
